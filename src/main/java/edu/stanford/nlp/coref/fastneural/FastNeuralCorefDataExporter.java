package edu.stanford.nlp.coref.fastneural;

import java.io.File;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
//import java.util.Iterator;
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
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.coref.statistical.CompressedFeatureVector;
import edu.stanford.nlp.coref.statistical.Compressor;
import edu.stanford.nlp.coref.statistical.DocumentExamples;
import edu.stanford.nlp.coref.statistical.Example;
import edu.stanford.nlp.coref.statistical.FeatureExtractor;
import edu.stanford.nlp.coref.statistical.StatisticalCorefProperties;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
//import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

/** Writes CoNLL data to JSON for fastneural model training (using python/tensorflow). */
public class FastNeuralCorefDataExporter implements CorefDocumentProcessor {
  private final FeatureExtractor extractor;
  private final Compressor<String> compressor;
  private final int maxMentionDistance;
  private final int maxMentionDistanceWithStringMatch;
  private final PrintWriter dataWriter;
  private final PrintWriter goldClusterWriter;

  public FastNeuralCorefDataExporter(Properties props, Dictionaries dictionaries, Compressor<String> compressor,
        String dataPath, String goldClusterPath) {
    String wordCountsFile = StatisticalCorefProperties.wordCountsPath(props);
    int maxMentionDistance = CorefProperties.maxMentionDistance(props);
    int maxMentionDistanceWithStringMatch = CorefProperties.maxMentionDistanceWithStringMatch(props);
    try {
      this.compressor = compressor;
      this.extractor = new FeatureExtractor(props, dictionaries, null, wordCountsFile);
      this.maxMentionDistance = maxMentionDistance;
      this.maxMentionDistanceWithStringMatch = maxMentionDistanceWithStringMatch;
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
    Map<Pair<Integer, Integer>, Boolean> allPairs = CorefUtils.getLabeledMentionPairs(document);
    Map<Pair<Integer, Integer>, Boolean> pairs = new HashMap<>();
    for (Map.Entry<Integer, List<Integer>> e: CorefUtils.heuristicFilter(
        CorefUtils.getSortedMentions(document),
        maxMentionDistance, maxMentionDistanceWithStringMatch).entrySet()) {
      for (int m1 : e.getValue()) {
        Pair<Integer, Integer> pair = new Pair<Integer, Integer>(m1, e.getKey());
        pairs.put(pair, allPairs.get(pair));
      }
    }

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
          //.add("contained-in-other-mention", mentionsByHeadIndex.get(m.headIndex).stream()
          //    .anyMatch(m2 -> m != m2 && m.insideIn(m2)) ? 1 : 0)
          .build());
    }

    DocumentExamples examples = extractor.extract(0, document, pairs, compressor);
    JsonObjectBuilder mentionFeatures = Json.createObjectBuilder();
    for (Map.Entry<Integer, CompressedFeatureVector> e : examples.mentionFeatures.entrySet()) {
      JsonObjectBuilder features = Json.createObjectBuilder();
      for (int i = 0; i < e.getValue().keys.size(); i++) {
        features.add(String.valueOf(e.getValue().keys.get(i)), e.getValue().values.get(i));
      }
      mentionFeatures.add(String.valueOf(e.getKey()), features);
    }

    JsonObjectBuilder mentionPairs = Json.createObjectBuilder();
    for (Example e: examples.examples) {
      JsonObjectBuilder example = Json.createObjectBuilder()
          .add("mid1", e.mentionId1)
          .add("mid2", e.mentionId2);
      JsonObjectBuilder features = Json.createObjectBuilder();
      for (int i = 0; i < e.pairwiseFeatures.keys.size(); i++) {
        features.add(String.valueOf(e.pairwiseFeatures.keys.get(i)),
            e.pairwiseFeatures.values.get(i));
      }
      example.add("label", (int)(e.label));
      example.add("features", features);
      mentionPairs.add(String.valueOf(e.mentionId1) + " " + String.valueOf(e.mentionId2), example);
    }

    JsonObject docData = Json.createObjectBuilder()
        .add("sentences", sentences.build())
        .add("mentions", mentions.build())
        .add("pairs", mentionPairs.build())
        .add("mention_features", mentionFeatures.build())
        .build();
    dataWriter.println(docData);
    System.out.println("Writing " + dataWriter.toString());
  }


  private static JsonArray getSentenceArray(List<CoreLabel> sentence) {
    JsonArrayBuilder sentenceBuilder = Json.createArrayBuilder();
    sentence.stream().map(CoreLabel::word)
      .map(w -> w.equals("/.") ? "." : w)
      .map(w -> w.equals("/?") ? "?" : w)
      .forEach(sentenceBuilder::add);
    return sentenceBuilder.build();
  }

  @Override
  public void finish() throws Exception {
    dataWriter.close();
    goldClusterWriter.close();
  }

  @Override
  public String getName() {
    return "DataExporter";
  }

  public static void main(String[] args) throws Exception {
    Properties props = StringUtils.argsToProperties(args);
    props.setProperty("coref.maxMentionDistance", "50");
    props.setProperty("coref.maxMentionDistanceWithStringMatch", "1000");
    props.setProperty("coref.conllOutputPath", "/Users/kevinclark/Programming/research/coref/conll-2012/output");
    props.setProperty("coref.data", "/Users/kevinclark/Programming/research/coref/conll-2012");
    props.setProperty("coref.scorer", "/Users/kevinclark/Programming/research/coref/conll-2012/scorer/v8.01/scorer.pl");
    Dictionaries dictionaries = new Dictionaries(props);

    String outputPath = "/Users/kevinclark/Programming/research/coref/data";
    String dataPath = outputPath + "/raw/";
    String goldClusterPath = outputPath + "/gold/";
    String compressorPath = outputPath + "/";
    IOUtils.ensureDir(new File(outputPath));
    IOUtils.ensureDir(new File(dataPath));
    IOUtils.ensureDir(new File(goldClusterPath));
    IOUtils.ensureDir(new File(compressorPath));

    Compressor<String> compressor = new Compressor<String>();
    for (Dataset dataset : Arrays.asList(Dataset.TRAIN, Dataset.DEV, Dataset.TEST)) {
      CorefProperties.setInput(props, dataset);
      System.out.println(CorefProperties.getInputPath(props));
      new FastNeuralCorefDataExporter(
          props,
          dictionaries,
          compressor,
          dataPath + dataset.toString().toLowerCase(),
          goldClusterPath + dataset.toString().toLowerCase()).run(props, dictionaries);
    }
    writeCompressor(compressor, compressorPath +  "/compression");
  }


  public static void writeCompressor(Compressor<String> compressor, String compressorPath) {
    JsonObjectBuilder compressorIndex = Json.createObjectBuilder();
    for (Map.Entry<String, Integer> e: compressor.getIndex().entrySet()) {
      compressorIndex.add(e.getKey(), e.getValue());
    }
    try {
      PrintWriter compressorWriter = IOUtils.getPrintWriter(compressorPath);
      compressorWriter.println(compressorIndex.build());
      compressorWriter.close();
    } catch (Exception e) {
        throw new RuntimeException("Error creating data exporter", e);
    }
  }
}
