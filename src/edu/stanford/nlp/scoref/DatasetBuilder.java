package edu.stanford.nlp.scoref;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import edu.stanford.nlp.hcoref.data.CorefCluster;
import edu.stanford.nlp.hcoref.data.Document;
import edu.stanford.nlp.hcoref.data.Mention;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.Pair;

public class DatasetBuilder implements DocumentProcessor {
  private final int maxExamplesPerDocument;
  private final double minClassImbalancedPerDocument;
  private final Map<Integer, Map<Pair<Integer, Integer>, Boolean>> mentionPairs;
  private final Random random;

  public DatasetBuilder() {
    this(0, Integer.MAX_VALUE);
  }

  public DatasetBuilder(double minClassImbalancedPerDocument, int maxExamplesPerDocument) {
    this.maxExamplesPerDocument = maxExamplesPerDocument;
    this.minClassImbalancedPerDocument = minClassImbalancedPerDocument;
    mentionPairs = new HashMap<>();
    random = new Random(0);
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

    long numP = labeledPairs.keySet().stream().filter(m -> labeledPairs.get(m)).count();
    List<Pair<Integer, Integer>> negative = labeledPairs.keySet().stream()
        .filter(m -> !labeledPairs.get(m))
        .collect(Collectors.toList());
    int numN = negative.size();
    if (numP / (float) (numP + numN) < minClassImbalancedPerDocument) {
      numN = (int) (numP / minClassImbalancedPerDocument - numP);
      Collections.shuffle(negative);
      for (int i = numN; i < negative.size(); i++) {
        labeledPairs.remove(negative.get(i));
      }
    }

    Map<Integer, List<Integer>> mentionToCandidateAntecedents = new HashMap<>();
    for (Pair<Integer, Integer> pair : labeledPairs.keySet()) {
      List<Integer> candidateAntecedents = mentionToCandidateAntecedents.get(pair.second);
      if (candidateAntecedents == null) {
          candidateAntecedents = new ArrayList<>();
          mentionToCandidateAntecedents.put(pair.second, candidateAntecedents);
      }
      candidateAntecedents.add(pair.first);
    }

    List<Integer> mentions = new ArrayList<>(mentionToCandidateAntecedents.keySet());
    while (labeledPairs.size() > maxExamplesPerDocument) {
      int mention = mentions.remove(random.nextInt(mentions.size()));
      for (int candidateAntecedent : mentionToCandidateAntecedents.get(mention)) {
        labeledPairs.remove(new Pair<>(candidateAntecedent, mention));
      }
    }

    mentionPairs.put(id, labeledPairs);
  }

  @Override
  public void finish() throws Exception {
    IOUtils.writeObjectToFile(mentionPairs, StatisticalCorefTrainer.datasetFile);
  }
}
