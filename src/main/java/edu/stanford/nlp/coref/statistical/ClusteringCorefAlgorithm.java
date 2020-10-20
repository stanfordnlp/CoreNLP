package edu.stanford.nlp.coref.statistical;

import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import edu.stanford.nlp.coref.CorefAlgorithm;
import edu.stanford.nlp.coref.CorefUtils;
import edu.stanford.nlp.coref.data.Dictionaries;
import edu.stanford.nlp.coref.data.Document;
import edu.stanford.nlp.coref.statistical.ClustererDataLoader.ClustererDoc;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Pair;

/**
 * Builds up coreference clusters incrementally with agglomerative clustering.
 * The model is described in
 *
 * Kevin Clark and Christopher D. Manning. 2015.
 * <a href="http://nlp.stanford.edu/pubs/clark-manning-acl15-entity.pdf">
 * Entity-Centric Coreference Resolution with Model Stacking</a>.
 * In Association for Computational Linguistics.
 *
 * See {@link StatisticalCorefTrainer} for training a new model.
 *
 * @author Kevin Clark
 */
public class ClusteringCorefAlgorithm implements CorefAlgorithm {

  private final Clusterer clusterer;
  private final PairwiseModel classificationModel;
  private final PairwiseModel rankingModel;
  private final PairwiseModel anaphoricityModel;
  private final FeatureExtractor extractor;

  public ClusteringCorefAlgorithm(Properties props, Dictionaries dictionaries) {
    this(props, dictionaries,
        StatisticalCorefProperties.clusteringModelPath(props),
        StatisticalCorefProperties.classificationModelPath(props),
        StatisticalCorefProperties.rankingModelPath(props),
        StatisticalCorefProperties.anaphoricityModelPath(props),
        StatisticalCorefProperties.wordCountsPath(props));
  }

  public ClusteringCorefAlgorithm(Properties props, Dictionaries dictionaries, String clusteringPath,
      String classificationPath, String rankingPath, String anaphoricityPath,
      String wordCountsPath) {
    clusterer = new Clusterer(clusteringPath);
    classificationModel = PairwiseModel.newBuilder("classification",
        MetaFeatureExtractor.newBuilder().build())
        .modelPath(classificationPath).build();
    rankingModel = PairwiseModel.newBuilder("ranking",
        MetaFeatureExtractor.newBuilder().build())
        .modelPath(rankingPath).build();
    anaphoricityModel = PairwiseModel.newBuilder("anaphoricity",
        MetaFeatureExtractor.anaphoricityMFE())
        .modelPath(anaphoricityPath).build();
    extractor = new FeatureExtractor(props, dictionaries, null, wordCountsPath);
  }

  @Override
  public void runCoref(Document document) {
    Map<Pair<Integer, Integer>, Boolean> mentionPairs =
       CorefUtils.getUnlabeledMentionPairs(document);
    if (mentionPairs.size() == 0) {
        return;
    }
    Compressor<String> compressor = new Compressor<>();
    DocumentExamples examples = extractor.extract(0, document, mentionPairs, compressor);

    Counter<Pair<Integer, Integer>> classificationScores = new ClassicCounter<>();
    Counter<Pair<Integer, Integer>> rankingScores = new ClassicCounter<>();
    Counter<Integer> anaphoricityScores = new ClassicCounter<>();
    for (Example example : examples.examples) {
      CorefUtils.checkForInterrupt();
      Pair<Integer, Integer> mentionPair =
              new Pair<>(example.mentionId1, example.mentionId2);
      classificationScores.incrementCount(mentionPair, classificationModel
              .predict(example, examples.mentionFeatures, compressor));
      rankingScores.incrementCount(mentionPair, rankingModel
              .predict(example, examples.mentionFeatures, compressor));
      if (!anaphoricityScores.containsKey(example.mentionId2)) {
          anaphoricityScores.incrementCount(example.mentionId2, anaphoricityModel
                  .predict(new Example(example, false), examples.mentionFeatures, compressor));
      }
    }

    ClustererDoc doc = new ClustererDoc(0, classificationScores, rankingScores, anaphoricityScores,
        mentionPairs, null, document.predictedMentionsByID.entrySet().stream().collect(
            Collectors.toMap(Map.Entry::getKey, e -> e.getValue().mentionType.toString())));
    for (Pair<Integer, Integer> mentionPair : clusterer.getClusterMerges(doc)) {
      CorefUtils.mergeCoreferenceClusters(mentionPair, document);
    }
  }

}
