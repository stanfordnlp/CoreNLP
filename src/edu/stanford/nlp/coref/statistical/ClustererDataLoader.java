package edu.stanford.nlp.coref.statistical;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Pair;

/**
 * Loads the data used to train {@link Clusterer}.
 * @author Kevin Clark
 */
public class ClustererDataLoader {
  public static class ClustererDoc {
    public final int id;
    public final Counter<Pair<Integer, Integer>> classificationScores;
    public final Counter<Pair<Integer, Integer>> rankingScores;
    public final Counter<Integer> anaphoricityScores;
    public final List<List<Integer>> goldClusters;
    public final Map<Integer, List<Integer>> mentionToGold;
    public final List<Integer> mentions;
    public final Map<Integer, String> mentionTypes;
    public final Set<Pair<Integer, Integer>> positivePairs;
    public final Map<Integer, Integer> mentionIndices;

    public ClustererDoc(int id,
        Counter<Pair<Integer, Integer>> classificationScores,
        Counter<Pair<Integer, Integer>> rankingScores,
        Counter<Integer> anaphoricityScores,
        Map<Pair<Integer, Integer>, Boolean> labeledPairs,
        List<List<Integer>> goldClusters,
        Map<Integer, String> mentionTypes) {
      this.id = id;
      this.classificationScores = classificationScores;
      this.rankingScores = rankingScores;
      this.goldClusters = goldClusters;
      this.mentionTypes = mentionTypes;
      this.anaphoricityScores = anaphoricityScores;

      positivePairs = labeledPairs.keySet().stream().filter(p -> labeledPairs.get(p))
          .collect(Collectors.toSet());
      Set<Integer> mentionsSet = new HashSet<>();
      for (Pair<Integer, Integer> pair : labeledPairs.keySet()) {
        mentionsSet.add(pair.first);
        mentionsSet.add(pair.second);
      }

      mentions = new ArrayList<>(mentionsSet);
      Collections.sort(mentions, (m1, m2) -> {
        Pair<Integer, Integer> p = new Pair<>(m1, m2);
        return m1 == m2 ? 0 : (classificationScores.containsKey(p) ? -1 : 1);
      });
      mentionIndices = new HashMap<>();
      for (int i = 0; i < mentions.size(); i++) {
        mentionIndices.put(mentions.get(i), i);
      }

      mentionToGold = new HashMap<>();
      if (goldClusters != null) {
        for (List<Integer> gold : goldClusters) {
          for (int m : gold) {
            mentionToGold.put(m, gold);
          }
        }
      }
    }
  }

  public static List<ClustererDoc> loadDocuments(int maxDocs) throws Exception {
    Map<Integer, Map<Pair<Integer, Integer>, Boolean>> labeledPairs =
        IOUtils.readObjectFromFile(StatisticalCorefTrainer.datasetFile);
    Map<Integer, Map<Integer, String>> mentionTypes =
        IOUtils.readObjectFromFile(StatisticalCorefTrainer.mentionTypesFile);
    Map<Integer, List<List<Integer>>> goldClusters =
        IOUtils.readObjectFromFile(StatisticalCorefTrainer.goldClustersFile);
    Map<Integer, Counter<Pair<Integer, Integer>>> classificationScores =
        IOUtils.readObjectFromFile(StatisticalCorefTrainer.pairwiseModelsPath
            + StatisticalCorefTrainer.CLASSIFICATION_MODEL + "/"
            + StatisticalCorefTrainer.predictionsName + ".ser");
    Map<Integer, Counter<Pair<Integer, Integer>>> rankingScores =
        IOUtils.readObjectFromFile(StatisticalCorefTrainer.pairwiseModelsPath
            + StatisticalCorefTrainer.RANKING_MODEL + "/"
            + StatisticalCorefTrainer.predictionsName + ".ser");
    Map<Integer, Counter<Pair<Integer, Integer>>> anaphoricityScoresLoaded =
        IOUtils.readObjectFromFile(StatisticalCorefTrainer.pairwiseModelsPath
            + StatisticalCorefTrainer.ANAPHORICITY_MODEL + "/"
            + StatisticalCorefTrainer.predictionsName + ".ser");

    Map<Integer, Counter<Integer>> anaphoricityScores = new HashMap<>();
    for (Map.Entry<Integer, Counter<Pair<Integer, Integer>>> e : anaphoricityScoresLoaded.entrySet()) {
      Counter<Integer> scores = new ClassicCounter<>();
      e.getValue().entrySet().forEach(e2 -> {
        scores.incrementCount(e2.getKey().second, e2.getValue());
      });
      anaphoricityScores.put(e.getKey(), scores);
    }

    return labeledPairs.keySet().stream().sorted().limit(maxDocs).map(i -> new ClustererDoc(i,
        classificationScores.get(i), rankingScores.get(i), anaphoricityScores.get(i),
        labeledPairs.get(i), goldClusters.get(i), mentionTypes.get(i)))
        .collect(Collectors.toList());
  }
}
