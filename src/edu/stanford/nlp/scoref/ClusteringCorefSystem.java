package edu.stanford.nlp.scoref;

import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import edu.stanford.nlp.hcoref.data.Document;
import edu.stanford.nlp.scoref.ClustererDataLoader.ClustererDoc;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.RuntimeInterruptedException;

// TODO: some serializable model class
public class ClusteringCorefSystem extends StatisticalCorefSystem {
  private final Clusterer clusterer;
  private final PairwiseModel classificationModel;
  private final PairwiseModel rankingModel;
  private final PairwiseModel anaphoricityModel;
  private final FeatureExtractor extractor;

  public ClusteringCorefSystem(Properties props, String clusteringPath, String classificationPath,
      String rankingPath, String anaphoricityPath, String wordCountsPath) {
    super(props);
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
        StatisticalCorefUtils.getUnlabeledMentionPairs(document);
    // when the mention count is 0 or 1, just return since there is no coref work to be done
    if (mentionPairs.keySet().size() == 0) {
        return;
    }
    Compressor<String> compressor = new Compressor<>();
    DocumentExamples examples = extractor.extract(0, document, mentionPairs, compressor);

    Counter<Pair<Integer, Integer>> classificationScores = new ClassicCounter<>();
    Counter<Pair<Integer, Integer>> rankingScores = new ClassicCounter<>();
    Counter<Integer> anaphoricityScores = new ClassicCounter<>();
    for (Example example : examples.examples) {
      if (Thread.interrupted()) {  // Allow interrupting
        throw new RuntimeInterruptedException();
      }
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
      if (Thread.interrupted()) {  // Allow interrupting
        throw new RuntimeInterruptedException();
      }
      StatisticalCorefUtils.mergeCoreferenceClusters(mentionPair, document);
    }
  }
}
