package edu.stanford.nlp.scoref;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.hcoref.data.Dictionaries.MentionType;
import edu.stanford.nlp.hcoref.data.Document;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Pair;

public class BestFirstCorefSystem extends StatisticalCorefSystem {
  private final Map<Pair<Boolean, Boolean>, Double> thresholds;
  private final FeatureExtractor extractor;
  private final PairwiseModel classifier;
  private final int maxMentionDistance;

  public BestFirstCorefSystem(Properties props, String wordCountsFile, String modelFile,
      int maxMentionDistance, double threshold) {
    this(props, wordCountsFile, modelFile, maxMentionDistance, makeThresholds(threshold));
  }

  public BestFirstCorefSystem(Properties props, String wordCountsFile, String modelPath,
      int maxMentionDistance, Map<Pair<Boolean, Boolean>, Double> thresholds) {
    super(props);
    extractor = new FeatureExtractor(props, dictionaries, null, wordCountsFile);
    classifier = PairwiseModel.newBuilder("classifier",
        MetaFeatureExtractor.newBuilder().build()).modelPath(modelPath).build();
    this.maxMentionDistance = maxMentionDistance;
    this.thresholds = thresholds;
  }

  private static Map<Pair<Boolean, Boolean>, Double> makeThresholds(double threshold) {
    Map<Pair<Boolean, Boolean>, Double> thresholds = new HashMap<>();
    thresholds.put(new Pair<>(true, true), threshold);
    thresholds.put(new Pair<>(true, false), threshold);
    thresholds.put(new Pair<>(false, true), threshold);
    thresholds.put(new Pair<>(false, false), threshold);
    return thresholds;
  }

  public static int i = 0;

  @Override
  public void runCoref(Document document) {
    Compressor<String> compressor = new Compressor<>();
    DocumentExamples examples = extractor.extract(0, document,
        StatisticalCorefUtils.getUnlabeledMentionPairs(document, maxMentionDistance), compressor);
    Counter<Pair<Integer, Integer>> pairwiseScores = new ClassicCounter<>();
    for (Example mentionPair : examples.examples) {
      pairwiseScores.incrementCount(new Pair<>(mentionPair.mentionId1, mentionPair.mentionId2),
          classifier.predict(mentionPair, examples.mentionFeatures, compressor));
    }

    List<Pair<Integer, Integer>> mentionPairs = new ArrayList<>(pairwiseScores.keySet());
    Collections.sort(mentionPairs, (p1, p2) -> {
      double diff = pairwiseScores.getCount(p2) - pairwiseScores.getCount(p1);
      return diff == 0 ? 0 : (int) Math.signum(diff);
    });

    Set<Integer> seenAnaphors = new HashSet<>();
    for (Pair<Integer, Integer> pair : mentionPairs) {
      if (seenAnaphors.contains(pair.second)) {
        continue;
      }
      seenAnaphors.add(pair.second);
      MentionType mt1 = document.predictedMentionsByID.get(pair.first).mentionType;
      MentionType mt2 = document.predictedMentionsByID.get(pair.second).mentionType;
      if (pairwiseScores.getCount(pair) > thresholds.get(new Pair<>(mt1 == MentionType.PRONOMINAL,
          mt2 == MentionType.PRONOMINAL))) {
        StatisticalCorefUtils.mergeCoreferenceClusters(pair, document);
      }
    }
  }
}
