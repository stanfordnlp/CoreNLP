package edu.stanford.nlp.scoref;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import edu.stanford.nlp.hcoref.data.CorefCluster;
import edu.stanford.nlp.hcoref.data.Document;
import edu.stanford.nlp.hcoref.data.Mention;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Pair;

public class MetadataWriter implements DocumentProcessor {
  private final Map<Integer, Map<Integer, String>> mentionTypes;
  private final Map<Integer, List<List<Integer>>> goldClusters;
  private final Counter<String> wordCounts;
  private final Map<Integer, Map<Pair<Integer, Integer>, Boolean>> mentionPairs;
  private final boolean countWords;

  public MetadataWriter(boolean countWords) {
    this.countWords = countWords;
    mentionTypes = new HashMap<>();
    goldClusters = new HashMap<>();
    wordCounts = new ClassicCounter<>();
    try {
      mentionPairs = IOUtils.readObjectFromFile(StatisticalCorefTrainer.datasetFile);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void process(int id, Document document) {
    Map<Pair<Integer, Integer>, Boolean> labeledPairs =
        StatisticalCorefUtils.getUnlabeledMentionPairs(document);
    for (CorefCluster c : document.goldCorefClusters.values()) {
      List<Mention> clusterMentions = new ArrayList<>(c.getCorefMentions());
      for (int i = 0; i < clusterMentions.size(); i++) {
        for (Mention clusterMention : clusterMentions) {
          Pair<Integer, Integer> mentionPair = new Pair<>(
                  clusterMentions.get(i).mentionID, clusterMention.mentionID);
          if (labeledPairs.containsKey(mentionPair)) {
            labeledPairs.put(mentionPair, true);
          }
        }
      }
    }
    Map<Pair<Integer, Integer>, Boolean> savedPairs = mentionPairs.get(id);
    for (Map.Entry<Pair<Integer, Integer>, Boolean> e: savedPairs.entrySet()) {
      Pair<Integer, Integer> pair = e.getKey();
      boolean label = e.getValue();
      assert(pair.first >= 0 && pair.second >= 0);
      assert(label == labeledPairs.get(pair));
    }

    mentionTypes.put(id, document.predictedMentionsByID.entrySet().stream().collect(
        Collectors.toMap(Map.Entry::getKey, e -> e.getValue().mentionType.toString())));

    // Gold clusters
    List<List<Integer>> clusters = new ArrayList<>();
    for (CorefCluster c : document.goldCorefClusters.values()) {
        List<Integer> cluster = new ArrayList<>();
        for (Mention m : c.getCorefMentions()) {
            cluster.add(m.mentionID);
        }
        clusters.add(cluster);
    }
    goldClusters.put(id, clusters);

    // Word counting
    if (countWords && mentionPairs.containsKey(id)) {
      Set<Pair<Integer, Integer>> pairs = mentionPairs.get(id).keySet();
      Set<Integer> mentions = new HashSet<>();
      for (Pair<Integer, Integer> pair : pairs) {
        mentions.add(pair.first);
        mentions.add(pair.second);
        Mention m1 = document.predictedMentionsByID.get(pair.first);
        Mention m2 = document.predictedMentionsByID.get(pair.second);
        wordCounts.incrementCount("h_" + m1.headWord.word().toLowerCase() + "_"
            + m2.headWord.word().toLowerCase());
      }

      Map<Integer, List<CoreLabel>> sentences = new HashMap<>();
      for (int mention : mentions) {
        Mention m = document.predictedMentionsByID.get(mention);
        if (!sentences.containsKey(m.sentNum)) {
          sentences.put(m.sentNum, m.sentenceWords);
        }
      }

      for (List<CoreLabel> sentence : sentences.values()) {
        for (int i = 0; i < sentence.size(); i++) {
          CoreLabel cl = sentence.get(i);
          if (cl == null) {
            continue;
          }
          String w = cl.word().toLowerCase();
          wordCounts.incrementCount(w);
          if (i > 0) {
            CoreLabel clp = sentence.get(i - 1);
            if (clp == null) {
              continue;
            }
            String wp = clp.word().toLowerCase();
            wordCounts.incrementCount(wp + "_" + w);
          }
        }
      }
    }
  }

  @Override
  public void finish() throws Exception {
    IOUtils.writeObjectToFile(mentionTypes, StatisticalCorefTrainer.mentionTypesFile);
    IOUtils.writeObjectToFile(goldClusters, StatisticalCorefTrainer.goldClustersFile);
    if (countWords) {
      IOUtils.writeObjectToFile(wordCounts, StatisticalCorefTrainer.wordCountsFile);
    }
  }
}
