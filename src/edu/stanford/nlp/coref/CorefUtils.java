package edu.stanford.nlp.coref;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import edu.stanford.nlp.coref.data.CorefCluster;
import edu.stanford.nlp.coref.data.Document;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.RuntimeInterruptedException;

/**
 * Useful utilities for coreference resolution.
 * @author Kevin Clark
 */
public class CorefUtils {
  public static List<Mention> getSortedMentions(Document document) {
    List<Mention> mentions = new ArrayList<>(document.predictedMentionsByID.values());
    Collections.sort(mentions, (m1, m2) -> m1.appearEarlierThan(m2) ? -1 : 1);
    return mentions;
  }

  public static List<Pair<Integer, Integer>> getMentionPairs(Document document) {
     List<Pair<Integer, Integer>> pairs = new ArrayList<>();
     List<Mention> mentions = getSortedMentions(document);
     for (int i = 0; i < mentions.size(); i++) {
       for (int j = 0; j < i; j++) {
         pairs.add(new Pair<>(mentions.get(j).mentionID, mentions.get(i).mentionID));
       }
     }
     return pairs;
   }

   public static Map<Pair<Integer, Integer>, Boolean> getUnlabeledMentionPairs(Document document) {
     return CorefUtils.getMentionPairs(document).stream()
         .collect(Collectors.toMap(p -> p, p -> false));
   }

   public static Map<Pair<Integer, Integer>, Boolean> getLabeledMentionPairs(Document document) {
     Map<Pair<Integer, Integer>, Boolean> mentionPairs = getUnlabeledMentionPairs(document);
     for (CorefCluster c : document.goldCorefClusters.values()) {
       List<Mention> clusterMentions = new ArrayList<>(c.getCorefMentions());
       for (Mention clusterMention : clusterMentions) {
         for (Mention clusterMention2 : clusterMentions) {
           Pair<Integer, Integer> mentionPair = new Pair<>(
               clusterMention.mentionID, clusterMention2.mentionID);
           if (mentionPairs.containsKey(mentionPair)) {
             mentionPairs.put(mentionPair, true);
           }
         }
       }
     }
     return mentionPairs;
   }

  public static void mergeCoreferenceClusters(Pair<Integer, Integer> mentionPair,
      Document document) {
    Mention m1 = document.predictedMentionsByID.get(mentionPair.first);
    Mention m2 = document.predictedMentionsByID.get(mentionPair.second);
    if (m1.corefClusterID == m2.corefClusterID) {
      return;
    }

    int removeId = m1.corefClusterID;
    CorefCluster c1 = document.corefClusters.get(m1.corefClusterID);
    CorefCluster c2 = document.corefClusters.get(m2.corefClusterID);
    CorefCluster.mergeClusters(c2, c1);
    document.corefClusters.remove(removeId);
  }

  public static void removeSingletonClusters(Document document) {
    for (CorefCluster c : new ArrayList<>(document.corefClusters.values())) {
      if (c.getCorefMentions().size() == 1) {
        document.corefClusters.remove(c.clusterID);
      }
    }
  }

  public static void checkForInterrupt() {
    if (Thread.interrupted()) {
      throw new RuntimeInterruptedException();
    }
  }

  public static Map<Integer, List<Integer>> heuristicFilter(List<Mention> sortedMentions,
      int maxMentionDistance, int maxMentionDistanceWithStringMatch) {
    Map<String, List<Mention>> wordToMentions = new HashMap<>();
    for (int i = 0; i < sortedMentions.size(); i++) {
      Mention m = sortedMentions.get(i);
      for (String word : getContentWords(m)) {
        wordToMentions.putIfAbsent(word, new ArrayList<>());
        wordToMentions.get(word).add(m);
      }
    }

    Map<Integer, List<Integer>> mentionToCandidateAntecedents = new HashMap<>();
    for (int i = 0; i < sortedMentions.size(); i++) {
      Mention m = sortedMentions.get(i);
      List<Integer> candidateAntecedents = new ArrayList<>();
      for (int j = Math.max(0, i - maxMentionDistance); j < i; j++) {
        candidateAntecedents.add(sortedMentions.get(j).mentionID);
      }
      for (String word : getContentWords(m)) {
        List<Mention> withStringMatch = wordToMentions.get(word);
        if (withStringMatch != null) {
          for (Mention match : withStringMatch) {
            if (match.mentionNum < m.mentionNum
                && match.mentionNum >= m.mentionNum - maxMentionDistanceWithStringMatch) {
              if (!candidateAntecedents.contains(match.mentionID)) {
                candidateAntecedents.add(match.mentionID);
              }
            }
          }
        }
      }
      if (!candidateAntecedents.isEmpty()) {
        mentionToCandidateAntecedents.put(m.mentionID, candidateAntecedents);
      }
    }
    return mentionToCandidateAntecedents;
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
