package edu.stanford.nlp.coref.statistical;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import edu.stanford.nlp.coref.CorefDocumentProcessor;
import edu.stanford.nlp.coref.data.CorefCluster;
import edu.stanford.nlp.coref.data.Document;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Pair;

/**
 * Writes various pieces of information about coreference documents to disk.
 * @author Kevin Clark
 */
public class MetadataWriter implements CorefDocumentProcessor {
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
    // Mention types
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
