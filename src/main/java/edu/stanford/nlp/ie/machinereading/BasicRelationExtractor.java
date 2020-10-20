package edu.stanford.nlp.ie.machinereading;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.ie.machinereading.structure.*;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.ArgumentParser.Option;

public class BasicRelationExtractor implements Extractor {

  private static final long serialVersionUID = 2606577772115897869L;

  private static final Logger logger = Logger.getLogger(BasicRelationExtractor.class.getName());

  protected LinearClassifier<String, String> classifier;

  @Option(name="featureCountThreshold", gloss="feature count threshold to apply to dataset")
  public int featureCountThreshold = 2;

  @Option(name="featureFactory", gloss="Feature factory for the relation extractor")
  public RelationFeatureFactory featureFactory;
  /**
   * strength of the prior on the linear classifier (passed to LinearClassifierFactory) or the C constant if relationExtractorClassifierType=svm
   */
  @Option(name="sigma", gloss="strength of the prior on the linear classifier (passed to LinearClassifierFactory) or the C constant if relationExtractorClassifierType=svm")
  public double sigma = 1.0;

  /**
   * which classifier to use (can be 'linear' or 'svm')
   */
  public String relationExtractorClassifierType = "linear";
  

  /**
   * If true, it creates automatically negative examples by generating all combinations between EntityMentions in a sentence
   * This is the common behavior, but for some domain (i.e., KBP) it must disabled. In these domains, the negative relation examples are created in the reader
   */
  protected boolean createUnrelatedRelations;

  /** Verifies that predicted labels are compatible with the relation arguments */
  private LabelValidator validator;

  protected RelationMentionFactory relationMentionFactory;

  public void setValidator(LabelValidator lv) { validator = lv; }
  public void setRelationExtractorClassifierType(String s) { relationExtractorClassifierType = s; }
  public void setFeatureCountThreshold(int i) {featureCountThreshold = i; }
  public void setSigma(double d) { sigma = d; }

  public BasicRelationExtractor(RelationFeatureFactory featureFac, Boolean createUnrelatedRelations, RelationMentionFactory factory) {
    featureFactory = featureFac;
    this.createUnrelatedRelations = createUnrelatedRelations;
    this.relationMentionFactory = factory;

    logger.setLevel(Level.INFO);
  }

  public void setCreateUnrelatedRelations(boolean b) {
    createUnrelatedRelations = b;
  }

  public static BasicRelationExtractor load(String modelPath) throws IOException, ClassNotFoundException {
    return IOUtils.readObjectFromURLOrClasspathOrFileSystem(modelPath);
  }

  @Override
  public void save(String modelpath) throws IOException {
    // make sure modelpath directory exists
    int lastSlash = modelpath.lastIndexOf(File.separator);
    if(lastSlash > 0){
      String path = modelpath.substring(0, lastSlash);
      File f = new File(path);
      if (! f.exists()) {
        f.mkdirs();
      }
    }

    FileOutputStream fos = new FileOutputStream(modelpath);
    ObjectOutputStream out = new ObjectOutputStream(fos);
    out.writeObject(this);
    out.close();
  }

  /**
   * Train on a list of ExtractionSentence containing labeled RelationMention objects
   */
  @Override
  public void train(Annotation sentences) {
    // Train a single multi-class classifier
    GeneralDataset<String, String> trainSet = createDataset(sentences);
    trainMulticlass(trainSet);
  }

  public void trainMulticlass(GeneralDataset<String, String> trainSet) {
    if (relationExtractorClassifierType.equalsIgnoreCase("linear")) {
      LinearClassifierFactory<String, String> lcFactory = new LinearClassifierFactory<>(1e-4, false, sigma);
      lcFactory.setVerbose(false);
      // use in-place SGD instead of QN. this is faster but much worse!
      // lcFactory.useInPlaceStochasticGradientDescent(-1, -1, 1.0);
      // use a hybrid minimizer: start with in-place SGD, continue with QN
      // lcFactory.useHybridMinimizerWithInPlaceSGD(50, -1, sigma);
      classifier = lcFactory.trainClassifier(trainSet);
    } else if (relationExtractorClassifierType.equalsIgnoreCase("svm")) {
      SVMLightClassifierFactory<String, String> svmFactory = new SVMLightClassifierFactory<>();
      svmFactory.setC(sigma);
      classifier = svmFactory.trainClassifier(trainSet);
    } else {
      throw new RuntimeException("Invalid classifier type: " + relationExtractorClassifierType);
    }
    if (logger.isLoggable(Level.FINE)) {
      reportWeights(classifier, null);
    }
  }

  protected static void reportWeights(LinearClassifier<String, String> classifier, String classLabel) {
    if (classLabel != null) logger.fine("CLASSIFIER WEIGHTS FOR LABEL " + classLabel);
    Map<String, Counter<String>> labelsToFeatureWeights = classifier.weightsAsMapOfCounters();
    List<String> labels = new ArrayList<>(labelsToFeatureWeights.keySet());
    Collections.sort(labels);
    for (String label: labels) {
      Counter<String> featWeights = labelsToFeatureWeights.get(label);
      List<Pair<String, Double>> sorted = Counters.toSortedListWithCounts(featWeights);
      StringBuilder bos = new StringBuilder();
      bos.append("WEIGHTS FOR LABEL ").append(label).append(':');
      for (Pair<String, Double> feat: sorted) {
        bos.append(' ').append(feat.first()).append(':').append(feat.second()+"\n");
      }
      logger.fine(bos.toString());
    }
  }

  protected String classOf(Datum<String, String> datum, ExtractionObject rel) {
    Counter<String> probs = classifier.probabilityOf(datum);
    List<Pair<String, Double>> sortedProbs = Counters.toDescendingMagnitudeSortedListWithCounts(probs);
    double nrProb = probs.getCount(RelationMention.UNRELATED);
    for(Pair<String, Double> choice: sortedProbs){
      if(choice.first.equals(RelationMention.UNRELATED)) return choice.first;
      if(nrProb >= choice.second) return RelationMention.UNRELATED; // no prediction, all probs have the same value
      if(compatibleLabel(choice.first, rel)) return choice.first;
    }
    return RelationMention.UNRELATED;
  }

  private boolean compatibleLabel(String label, ExtractionObject rel) {
    if(rel == null) return true;
    if(validator != null) return validator.validLabel(label, rel);
    return true;
  }

  protected Counter<String> probabilityOf(Datum<String, String> testDatum) {
    return classifier.probabilityOf(testDatum);
  }

  protected void justificationOf(Datum<String, String> testDatum, PrintWriter pw, String label) {
    classifier.justificationOf(testDatum, pw);
  }

  /**
   * Predict a relation for each pair of entities in the sentence; including relations of type unrelated.
   * This creates new RelationMention objects!
   */
  protected List<RelationMention> extractAllRelations(CoreMap sentence) {
    List<RelationMention> extractions = new ArrayList<>();

    List<RelationMention> cands = null;
    if(createUnrelatedRelations){
      // creates all possible relations between all entities in the sentence
      cands = AnnotationUtils.getAllUnrelatedRelations(relationMentionFactory, sentence, false);
    } else {
      // just take the candidates produced by the reader (in KBP)
      cands = sentence.get(MachineReadingAnnotations.RelationMentionsAnnotation.class);
      if(cands == null){
        cands = new ArrayList<>();
      }
    }

    // the actual classification takes place here!
    for (RelationMention rel : cands) {
      Datum<String, String> testDatum = createDatum(rel);
      String label = classOf(testDatum, rel);
      Counter<String> probs = probabilityOf(testDatum);
      double prob = probs.getCount(label);
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      if (logger.isLoggable(Level.INFO)) {
        justificationOf(testDatum, pw, label);
      }
      logger.info("Current sentence: " + AnnotationUtils.tokensAndNELabelsToString(rel.getArg(0).getSentence()) + "\n"
              + "Classifying relation: " + rel + "\n"
              + "JUSTIFICATION for label GOLD:" + rel.getType() + " SYS:" + label + " (prob:" + prob + "):\n"
              + sw.toString());
      logger.info("Justification done.");
      RelationMention relation = relationMentionFactory.constructRelationMention(
              rel.getObjectId(),
              sentence,
              rel.getExtent(),
              label,
              null,
              rel.getArgs(),
              probs);
      extractions.add(relation);

      if(! relation.getType().equals(rel.getType())){
        logger.info("Classification: found different type " + relation.getType() + " for relation: " + rel);
        logger.info("The predicted relation is: " + relation);
        logger.info("Current sentence: " + AnnotationUtils.tokensAndNELabelsToString(rel.getArg(0).getSentence()));
      } else{
        logger.info("Classification: found similar type " + relation.getType() + " for relation: " + rel);
        logger.info("The predicted relation is: " + relation);
        logger.info("Current sentence: " + AnnotationUtils.tokensAndNELabelsToString(rel.getArg(0).getSentence()));
      }
    }
    return extractions;
  }

  public List<String> annotateMulticlass(List<Datum<String, String>> testDatums) {
    List<String> predictedLabels = new ArrayList<>();

    for (Datum<String, String> testDatum: testDatums) {
      String label = classOf(testDatum, null);
      Counter<String> probs = probabilityOf(testDatum);
      double prob = probs.getCount(label);

      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      if (logger.isLoggable(Level.FINE)) {
        justificationOf(testDatum, pw, label);
      }
      logger.fine("JUSTIFICATION for label GOLD:" + testDatum.label() + " SYS:" + label + " (prob:" + prob + "):\n"
              + sw.toString() + "\nJustification done.");
      predictedLabels.add(label);

      if(! testDatum.label().equals(label)){
        logger.info("Classification: found different type " + label + " for relation: " + testDatum);
      } else{
        logger.info("Classification: found similar type " + label + " for relation: " + testDatum);
      }
    }

    return predictedLabels;
  }

  public void annotateSentence(CoreMap sentence) {
    // this stores all relation mentions generated by this extractor
    List<RelationMention> relations = new ArrayList<>();

    // extractAllRelations creates new objects for every predicted relation
    for (RelationMention rel : extractAllRelations(sentence)) {
      // add all relations. potentially useful for a joint model
      // if (! RelationMention.isUnrelatedLabel(rel.getType()))
      relations.add(rel);

    }

    // caution: this removes the old list of relation mentions!
    for (RelationMention r: relations) {
      if (! r.getType().equals(RelationMention.UNRELATED)) {
        logger.fine("Found positive relation in annotateSentence: " + r);
      }
    }
    sentence.set(MachineReadingAnnotations.RelationMentionsAnnotation.class, relations);
  }

  @Override
  public void annotate(Annotation dataset) {
    for (CoreMap sentence : dataset.get(CoreAnnotations.SentencesAnnotation.class)){
      annotateSentence(sentence);
    }
  }

  protected GeneralDataset<String, String> createDataset(Annotation corpus) {
    GeneralDataset<String, String> dataset = new RVFDataset<>();

    for (CoreMap sentence : corpus.get(CoreAnnotations.SentencesAnnotation.class)) {
      for (RelationMention rel : AnnotationUtils.getAllRelations(relationMentionFactory, sentence, createUnrelatedRelations)) {
        dataset.add(createDatum(rel));
      }
    }

    dataset.applyFeatureCountThreshold(featureCountThreshold);
    return dataset;
  }

  protected Datum<String, String> createDatum(RelationMention rel) {
    assert(featureFactory != null);
    return featureFactory.createDatum(rel);
  }

  protected Datum<String, String> createDatum(RelationMention rel, String label) {
    assert(featureFactory != null);
    Datum<String, String> datum = featureFactory.createDatum(rel, label);
    return datum;
  }

  @Override
  public void setLoggerLevel(Level level) {
    logger.setLevel(level);
  }


}
