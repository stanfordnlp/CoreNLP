package edu.stanford.nlp.scoref;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.hcoref.data.CorefCluster;
import edu.stanford.nlp.hcoref.data.Document;
import edu.stanford.nlp.hcoref.data.Mention;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;

public class StatisticalCorefUtils {
  public static List<Mention> getSortedMentions(Document document) {
    List<Mention> mentions = new ArrayList<>(document.predictedMentionsByID.values());
    Collections.sort(mentions, (m1, m2) -> m1.appearEarlierThan(m2) ? -1 : 1);
    return mentions;
  }

  public static Map<Pair<Integer, Integer>, Boolean> getUnlabeledMentionPairs(Document document) {
    return getUnlabeledMentionPairs(document, Integer.MAX_VALUE);
  }

  public static Map<Pair<Integer, Integer>, Boolean> getUnlabeledMentionPairs(Document document,
      int maxMentionDistance) {
    Map<Pair<Integer, Integer>, Boolean> pairs = new HashMap<>();
    List<Mention> mentions = getSortedMentions(document);
    for (int i = 0; i < mentions.size(); i++) {
      for (int j = Math.max(0, i - maxMentionDistance); j < i; j++) {
        pairs.put(new Pair<>(mentions.get(j).mentionID, mentions.get(i).mentionID), false);
      }
    }
    return pairs;
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

  public static String fieldValues(Object o) {
    String s = "";
    Field[] fields = o.getClass().getDeclaredFields();
    for (Field field : fields) {
      try {
        field.setAccessible(true);
        s += field.getName() + " = " + field.get(o) + "\n";
      } catch (Exception e) {
        throw new RuntimeException("Error getting field value for " + field.getName(), e);
      }
    }

    return s;
  }

  private static StanfordCoreNLP pipeline = null;
  public static StanfordCoreNLP pipeline() {
    if (pipeline == null) {
      Properties coreNLPProperties = new Properties();
      coreNLPProperties.setProperty("annotators", "");
      coreNLPProperties.setProperty("threads", "1");
      coreNLPProperties.remove("threads");
      coreNLPProperties.setProperty("annotators", coreNLPProperties.getProperty("annotators.noxml"));
      pipeline = new StanfordCoreNLP(coreNLPProperties);
    }
    return pipeline;
  }

  public static Annotation reAnnotate(edu.stanford.nlp.dcoref.Document document) {
    Annotation newAnn = new Annotation("pos, lemma, ner, depparse");
    List<CoreMap> newSentences = new ArrayList<>();
    List<CoreLabel> newTokens = new ArrayList<>();
    List<CoreMap> sentences = document.annotation.get(CoreAnnotations.SentencesAnnotation.class);
    for (int i = 0; i < sentences.size(); i++) {
      CoreMap sentence = sentences.get(i);
      Annotation sentenceAnn = new Annotation(sentence.get(CoreAnnotations.TextAnnotation.class));
      pipeline().annotate(sentenceAnn);
      CoreMap newSentence = sentenceAnn.get(CoreAnnotations.SentencesAnnotation.class).get(0);
      newSentences.add(newSentence);
      newTokens.addAll(newSentence.get(CoreAnnotations.TokensAnnotation.class));
    }
    newAnn.set(CoreAnnotations.SentencesAnnotation.class, newSentences);
    newAnn.set(CoreAnnotations.TokensAnnotation.class, newTokens);

    return newAnn;
  }
}
