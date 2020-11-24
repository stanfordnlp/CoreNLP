package edu.stanford.nlp.coref.neural;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import edu.stanford.nlp.coref.CorefDocumentProcessor;
import edu.stanford.nlp.coref.CorefProperties;
import edu.stanford.nlp.coref.CorefProperties.Dataset;
import edu.stanford.nlp.coref.CorefUtils;
import edu.stanford.nlp.coref.data.CorefCluster;
import edu.stanford.nlp.coref.data.Dictionaries;
import edu.stanford.nlp.coref.data.Document;
import edu.stanford.nlp.coref.data.Document.DocType;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

/**
 * Outputs the CoNLL data for training the neural coreference system
 * (implemented in python/theano).
 * See <a href="https://github.com/clarkkev/deep-coref">https://github.com/clarkkev/deep-coref</a>
 * for the training code.
 *
 * @author Kevin Clark
 */
public class NeuralCorefDataExporter implements CorefDocumentProcessor {

  private final boolean conll;
  private final PrintWriter dataWriter;
  private final PrintWriter goldClusterWriter;
  private final Dictionaries dictionaries;

  public NeuralCorefDataExporter(Properties props, Dictionaries dictionaries, String dataPath,
      String goldClusterPath) {
    conll = CorefProperties.conll(props);
    this.dictionaries = dictionaries;
    try {
      dataWriter = IOUtils.getPrintWriter(dataPath);
      goldClusterWriter = IOUtils.getPrintWriter(goldClusterPath);
    } catch (Exception e) {
        throw new RuntimeException("Error creating data exporter", e);
    }
  }

  @Override
  public void process(int id, Document document) {
    JsonArrayBuilder clusters = Json.createArrayBuilder();
    for (CorefCluster gold : document.goldCorefClusters.values()) {
      JsonArrayBuilder c = Json.createArrayBuilder();
      for (Mention m : gold.corefMentions) {
        c.add(m.mentionID);
      }
      clusters.add(c.build());
    }
    goldClusterWriter.println(Json.createObjectBuilder().add(String.valueOf(id),
        clusters.build()).build());

    Map<Pair<Integer, Integer>, Boolean> mentionPairs = CorefUtils.getLabeledMentionPairs(document);
    List<Mention> mentionsList = CorefUtils.getSortedMentions(document);
    Map<Integer, List<Mention>> mentionsByHeadIndex = new HashMap<>();
    for (Mention m : mentionsList) {
      List<Mention> withIndex = mentionsByHeadIndex.computeIfAbsent(m.headIndex, k -> new ArrayList<>());
      withIndex.add(m);
    }

    JsonObjectBuilder docFeatures = Json.createObjectBuilder();
    docFeatures.add("doc_id", id);
    docFeatures.add("type", document.docType == DocType.ARTICLE ? 1 : 0);
    docFeatures.add("source", document.docInfo.get("DOC_ID").split("/")[0]);

    JsonArrayBuilder sentences = Json.createArrayBuilder();
    for (CoreMap sentence : document.annotation.get(SentencesAnnotation.class)) {
      sentences.add(getSentenceArray(sentence.get(CoreAnnotations.TokensAnnotation.class)));
    }

    JsonObjectBuilder mentions = Json.createObjectBuilder();
    for (Mention m : document.predictedMentionsByID.values()) {
      Iterator<SemanticGraphEdge> iterator =
          m.enhancedDependency.incomingEdgeIterator(m.headIndexedWord);
      SemanticGraphEdge relation = iterator.hasNext() ? iterator.next() : null;
      String depRelation = relation == null ? "no-parent" : relation.getRelation().toString();
      String depParent = relation == null ? "<missing>" : relation.getSource().word();

      mentions.add(String.valueOf(m.mentionNum), Json.createObjectBuilder()
          .add("doc_id", id)
          .add("mention_id", m.mentionID)
          .add("mention_num", m.mentionNum)
          .add("sent_num", m.sentNum)
          .add("start_index", m.startIndex)
          .add("end_index", m.endIndex)
          .add("head_index", m.headIndex)
          .add("mention_type", m.mentionType.toString())
          .add("dep_relation", depRelation)
          .add("dep_parent", depParent)
          .add("sentence", getSentenceArray(m.sentenceWords))
          .add("contained-in-other-mention", mentionsByHeadIndex.get(m.headIndex).stream()
              .anyMatch(m2 -> m != m2 && m.insideIn(m2)) ? 1 : 0)
          .build());
    }

    JsonArrayBuilder featureNames = Json.createArrayBuilder()
        .add("same-speaker")
        .add("antecedent-is-mention-speaker")
        .add("mention-is-antecedent-speaker")
        .add("relaxed-head-match")
        .add("exact-string-match")
        .add("relaxed-string-match");
    JsonObjectBuilder features = Json.createObjectBuilder();
    JsonObjectBuilder labels = Json.createObjectBuilder();

    for (Map.Entry<Pair<Integer, Integer>, Boolean> e : mentionPairs.entrySet()) {
      Mention m1 = document.predictedMentionsByID.get(e.getKey().first);
      Mention m2 = document.predictedMentionsByID.get(e.getKey().second);
      String key = m1.mentionNum + " " + m2.mentionNum;

      JsonArrayBuilder builder = Json.createArrayBuilder();
      for (int val : CategoricalFeatureExtractor.pairwiseFeatures(
          document, m1, m2, dictionaries)) {
        builder.add(val);
      }
      features.add(key, builder.build());
      labels.add(key, e.getValue() ? 1 : 0);
    }

    JsonObject docData = Json.createObjectBuilder()
        .add("sentences", sentences.build())
        .add("mentions", mentions.build())
        .add("labels", labels.build())
        .add("pair_feature_names", featureNames.build())
        .add("pair_features", features.build())
        .add("document_features", docFeatures.build())
        .build();
    dataWriter.println(docData);
  }

  @Override
  public void finish() throws Exception {
    dataWriter.close();
    goldClusterWriter.close();
  }

  private static JsonArray getSentenceArray(List<CoreLabel> sentence) {
    JsonArrayBuilder sentenceBuilder = Json.createArrayBuilder();
    sentence.stream().map(CoreLabel::word)
      .map(w -> w.equals("/.") ? "." : w)
      .map(w -> w.equals("/?") ? "?" : w)
      .forEach(sentenceBuilder::add);
    return sentenceBuilder.build();
  }

  public static void exportData(String outputPath, Dataset dataset, Properties props,
      Dictionaries dictionaries) throws Exception {
    CorefProperties.setInput(props, dataset);
    String dataPath = outputPath + "/data_raw/";
    String goldClusterPath = outputPath + "/gold/";
    IOUtils.ensureDir(new File(outputPath));
    IOUtils.ensureDir(new File(dataPath));
    IOUtils.ensureDir(new File(goldClusterPath));
    new NeuralCorefDataExporter(props, dictionaries,
        dataPath + dataset.toString().toLowerCase(),
        goldClusterPath + dataset.toString().toLowerCase()).run(props, dictionaries);
  }

  /**
   * Can be run in one of two ways:
   * <br>
   * java edu.stanford.nlp.coref.neural.NeuralCorefDataExporter coref.properties output.path
   * <br>
   * java edu.stanford.nlp.coref.neural.NeuralCorefDataExporter -props coref.properties -outputPath output.path otherargs...
   * <br>
   * The first formulation is for backwards compatibility with the documentation in clarkkev/deep-coref.
   *
   */
  public static void main(String[] args) throws Exception {
    final Properties props;
    final String outputPath;
    if (args.length == 2 && !args[0].startsWith("-")) {
      props = StringUtils.argsToProperties("-props", args[0]);
      outputPath = args[1];
    } else {
      props = StringUtils.argsToProperties(args);
      outputPath = props.getProperty("outputPath");
    }
    Dictionaries dictionaries = new Dictionaries(props);
    exportData(outputPath, Dataset.TRAIN, props, dictionaries);
    exportData(outputPath, Dataset.DEV, props, dictionaries);
    exportData(outputPath, Dataset.TEST, props, dictionaries);
  }

}
