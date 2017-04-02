package edu.stanford.nlp.coref.statistical;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.coref.data.Dictionaries.MentionType;
import edu.stanford.nlp.coref.data.Document;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.RuntimeInterruptedException;

public class BestFirstCorefSystem extends StatisticalCorefSystem {

  private final Map<Pair<Boolean, Boolean>, Double> thresholds;
  private final FeatureExtractor extractor;
  private final PairwiseModel classifier;
  private final int maxMentionDistance;
  private final int maxMentionDistanceWithStringMatch;

  public BestFirstCorefSystem(Properties props, String wordCountsFile, String modelFile,
      int maxMentionDistance, int maxMentionDistanceWithStringMatch, double threshold) {
    this(props, wordCountsFile, modelFile, maxMentionDistance, maxMentionDistanceWithStringMatch,
        new double[] {threshold, threshold, threshold, threshold});
  }

  public BestFirstCorefSystem(Properties props, String wordCountsFile, String modelPath,
      int maxMentionDistance, int maxMentionDistanceWithStringMatch, double[] thresholds) {
    super(props);
    extractor = new FeatureExtractor(props, dictionaries, null, wordCountsFile);
    classifier = PairwiseModel.newBuilder("classifier",
        MetaFeatureExtractor.newBuilder().build()).modelPath(modelPath).build();
    this.maxMentionDistance = maxMentionDistance;
    this.maxMentionDistanceWithStringMatch = maxMentionDistanceWithStringMatch;
    this.thresholds = makeThresholds(thresholds);
  }

  private static Map<Pair<Boolean, Boolean>, Double> makeThresholds(double[] thresholds) {
    Map<Pair<Boolean, Boolean>, Double> thresholdsMap = new HashMap<>();
    thresholdsMap.put(new Pair<>(true, true), thresholds[0]);
    thresholdsMap.put(new Pair<>(true, false), thresholds[1]);
    thresholdsMap.put(new Pair<>(false, true), thresholds[2]);
    thresholdsMap.put(new Pair<>(false, false), thresholds[3]);
    return thresholdsMap;
  }

  @Override
  public void runCoref(Document document) {
    Compressor<String> compressor = new Compressor<>();
    List<Mention> sortedMentions = StatisticalCorefUtils.getSortedMentions(document);
    if (Thread.interrupted()) {  // Allow interrupting
      throw new RuntimeInterruptedException();
    }
    for (int i = 0; i < sortedMentions.size(); i++) {
      sortedMentions.get(i).mentionNum = i;
    }

    Map<Pair<Integer, Integer>, Boolean> pairs =
        StatisticalCorefUtils.getUnlabeledMentionPairs(document, maxMentionDistance);
    if (maxMentionDistance != Integer.MAX_VALUE) {
      Map<String, List<Mention>> wordToMentions = new HashMap<>();
      for (Mention m : document.predictedMentionsByID.values()) {
        if (Thread.interrupted()) {  // Allow interrupting
          throw new RuntimeInterruptedException();
        }
        for (String word : getContentWords(m)) {
          wordToMentions.putIfAbsent(word, new ArrayList<>());
          wordToMentions.get(word).add(m);
        }
      }
      for (Mention m1 : document.predictedMentionsByID.values()) {
        if (Thread.interrupted()) {  // Allow interrupting
          throw new RuntimeInterruptedException();
        }
        for (String word : getContentWords(m1)) {
          List<Mention> ms = wordToMentions.get(word);
          if (ms != null) {
            for (Mention m2 : ms) {
              if (m1.mentionNum < m2.mentionNum
                  && m1.mentionNum >= m2.mentionNum - maxMentionDistanceWithStringMatch) {
                pairs.put(new Pair<>(m1.mentionID, m2.mentionID), false);
              }
            }
          }
        }
      }
    }

    DocumentExamples examples = extractor.extract(0, document, pairs, compressor);
    Counter<Pair<Integer, Integer>> pairwiseScores = new ClassicCounter<>();
    for (Example mentionPair : examples.examples) {
      if (Thread.interrupted()) {  // Allow interrupting
        throw new RuntimeInterruptedException();
      }
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
      if (Thread.interrupted()) {  // Allow interrupting
        throw new RuntimeInterruptedException();
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

  private static List<String> getContentWords(Mention m) {
    List<String> words = new ArrayList<>();
    for (int i = m.startIndex; i < m.endIndex; i++) {
      CoreLabel cl = m.sentenceWords.get(i);
      String POS = cl.get(CoreAnnotations.PartOfSpeechAnnotation.class);
      if (POS.equals("NN") || POS.equals("NNS") || POS.equals("NNP") || POS.equals("NNPS")) {
        words.add(cl.word().toLowerCase());
      }
    }
    return words;
  }

}
