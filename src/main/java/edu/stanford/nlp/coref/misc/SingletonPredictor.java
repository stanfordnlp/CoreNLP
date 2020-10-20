// StanfordCoreNLP -- a suite of NLP tools

package edu.stanford.nlp.coref.misc;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.classify.Dataset;
import edu.stanford.nlp.classify.GeneralDataset;
import edu.stanford.nlp.classify.LogisticClassifier;
import edu.stanford.nlp.classify.LogisticClassifierFactory;
import edu.stanford.nlp.coref.data.CorefCluster;
import edu.stanford.nlp.coref.data.Dictionaries;
import edu.stanford.nlp.coref.data.Document;
import edu.stanford.nlp.coref.data.DocumentMaker;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokenBeginAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Train the singleton predictor using a logistic regression classifier as
 * described in Recasens, de Marneffe and Potts, NAACL 2013
 * Label 0 = Singleton mention.
 * Label 1 = Coreferent mention.
 *
 * This is an example of the properties file for this class:
 *
 *     # This is set to true so that gold POS, gold parses and gold NEs are used.
 *     dcoref.replicate.conll = true
 *
 *     # Path to the directory containing the CoNLL training files.
 *     dcoref.conll2011 = /path/to/conll-2012/v4/data/train/data/english/annotations/
 *
 *     # Output file where the serialized model is saved.
 *     singleton.predictor.output = /path/to/predictor.output
 *
 * @author Marta Recasens
 * @author Marie-Catherine de Marneffe
 */
public class SingletonPredictor  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(SingletonPredictor.class);

  private SingletonPredictor() {} // static main utility

  /**
   * Set index for each token and sentence in the document.
   * @param doc
   */
  private static void setTokenIndices(Document doc) {
    int token_index = 0;
    for (CoreMap sent : doc.annotation.get(SentencesAnnotation.class)) {
      for (CoreLabel token : sent.get(TokensAnnotation.class)) {
        token.set(TokenBeginAnnotation.class, token_index++);
      }
    }
  }

  /**
   * Generate the training features from the CoNLL input file.
   * @return Dataset of feature vectors
   * @throws Exception
   */
  private static GeneralDataset<String, String> generateFeatureVectors(Properties props) throws Exception {

    GeneralDataset<String, String> dataset = new Dataset<>();

    Dictionaries dict = new Dictionaries(props);
    DocumentMaker docMaker = new DocumentMaker(props, dict);

    Document document;
    while((document = docMaker.nextDoc()) != null){
      setTokenIndices(document);
      Map<Integer, CorefCluster> entities = document.goldCorefClusters;

      // Generate features for coreferent mentions with class label 1
      for(CorefCluster entity : entities.values()){
        for(Mention mention : entity.getCorefMentions()){
          // Ignore verbal mentions
          if(mention.headWord.tag().startsWith("V")) continue;

          IndexedWord head = mention.enhancedDependency.
              getNodeByIndexSafe(mention.headWord.index());
          if(head == null) continue;
          ArrayList<String> feats = mention.getSingletonFeatures(dict);
          dataset.add(new BasicDatum<>(feats, "1"));
        }
      }

      // Generate features for singletons with class label 0
      ArrayList<CoreLabel> gold_heads = new ArrayList<>();
      for(Mention gold_men : document.goldMentionsByID.values()){
        gold_heads.add(gold_men.headWord);
      }
      for(Mention predicted_men : document.predictedMentionsByID.values()){
        SemanticGraph dep = predicted_men.enhancedDependency;
        IndexedWord head = dep.getNodeByIndexSafe(predicted_men.headWord.index());
        if(head == null || !dep.vertexSet().contains(head)) continue;

        // Ignore verbal mentions
        if(predicted_men.headWord.tag().startsWith("V")) continue;
        // If the mention is in the gold set, it is not a singleton and thus ignore
        if(gold_heads.contains(predicted_men.headWord)) continue;

        dataset.add(new BasicDatum<>(
                predicted_men.getSingletonFeatures(dict), "0"));
      }
    }

    dataset.summaryStatistics();
    return dataset;
  }

  /**
   * Train the singleton predictor using a logistic regression classifier.
   * @param pDataset Dataset of features
   * @return Singleton predictor
   */
  public static LogisticClassifier<String, String> train(GeneralDataset<String, String> pDataset){
    LogisticClassifierFactory<String, String> lcf =
            new LogisticClassifierFactory<>();
    LogisticClassifier<String, String> classifier = lcf.trainClassifier(pDataset);

    return classifier;
  }

  /**
   * Saves the singleton predictor model to the given filename.
   * If there is an error, a RuntimeIOException is thrown.
   */
  private static void saveToSerialized(LogisticClassifier<String, String> predictor,
                                       String filename) {
    try {
      log.info("Writing singleton predictor in serialized format to file " + filename + ' ');
      ObjectOutputStream out = IOUtils.writeStreamFromString(filename);
      out.writeObject(predictor);
      out.close();
      log.info("done.");
    } catch (IOException ioe) {
      throw new RuntimeIOException(ioe);
    }
  }

  private static String getPathSingletonPredictor(Properties props) {
    return PropertiesUtils.getString(props, "coref.path.singletonPredictor", "edu/stanford/nlp/models/dcoref/singleton.predictor.ser");
  }

  public static void main(String[] args) throws Exception {
    Properties props;
    if (args.length > 0) {
      props = StringUtils.argsToProperties(args);
    } else {
      props = new Properties();
    }
    if ( ! props.containsKey("dcoref.conll2011")) {
      log.info("-dcoref.conll2011 [input_CoNLL_corpus]: was not specified");
      return;
    }
    if ( ! props.containsKey("singleton.predictor.output")) {
      log.info("-singleton.predictor.output [output_model_file]: was not specified");
      return;
    }

    GeneralDataset<String, String> data = SingletonPredictor.generateFeatureVectors(props);
    LogisticClassifier<String, String> classifier = SingletonPredictor.train(data);
    SingletonPredictor.saveToSerialized(classifier, getPathSingletonPredictor(props));
  }

}
