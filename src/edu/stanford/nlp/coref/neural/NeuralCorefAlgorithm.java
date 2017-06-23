package edu.stanford.nlp.coref.neural;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.coref.CorefAlgorithm;
import edu.stanford.nlp.coref.CorefProperties;
import edu.stanford.nlp.coref.CorefUtils;
import edu.stanford.nlp.coref.data.Dictionaries;
import edu.stanford.nlp.coref.data.Document;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Pair;

public class NeuralCorefAlgorithm implements CorefAlgorithm {
  private final double greedyness;
  private final int maxMentionDistance;
  private final int maxMentionDistanceWithStringMatch;

  private final CategoricalFeatureExtractor featureExtractor;
  private final EmbeddingExtractor embeddingExtractor;
  private final NeuralCorefModel model;

  public NeuralCorefAlgorithm(Properties props, Dictionaries dictionaries) {
    greedyness = NeuralCorefProperties.greedyness(props);
    maxMentionDistance = CorefProperties.maxMentionDistance(props);
    maxMentionDistanceWithStringMatch = CorefProperties.maxMentionDistanceWithStringMatch(props);

    try {
      model = IOUtils.readObjectFromURLOrClasspathOrFileSystem(
          NeuralCorefProperties.modelPath(props));
      embeddingExtractor = new EmbeddingExtractor(CorefProperties.conll(props),
          IOUtils.readObjectFromURLOrClasspathOrFileSystem(
              NeuralCorefProperties.pretrainedEmbeddingsPath(props)),
          model.getWordEmbeddings());
    } catch (ClassNotFoundException | IOException e) {
      throw new RuntimeException("Error initializing NeuralCorefAlgorithm", e);
    }
    featureExtractor = new CategoricalFeatureExtractor(props, dictionaries);
  }

  @Override
  public void runCoref(Document document) {
    List<Mention> sortedMentions = CorefUtils.getSortedMentions(document);
    Map<Integer, List<Mention>> mentionsByHeadIndex = new HashMap<>();
    for (Mention m : sortedMentions) {
      List<Mention> withIndex = mentionsByHeadIndex.get(m.headIndex);
      if (withIndex == null) {
        withIndex = new ArrayList<>();
        mentionsByHeadIndex.put(m.headIndex, withIndex);
      }
      withIndex.add(m);
    }

    SimpleMatrix documentEmbedding = embeddingExtractor.getDocumentEmbedding(document);
    Map<Integer, SimpleMatrix> antecedentEmbeddings = new HashMap<>();
    Map<Integer, SimpleMatrix> anaphorEmbeddings = new HashMap<>();
    Counter<Integer> anaphoricityScores = new ClassicCounter<>();
    for (Mention m : sortedMentions) {
      SimpleMatrix mentionEmbedding = embeddingExtractor.getMentionEmbeddings(m, documentEmbedding);
      antecedentEmbeddings.put(m.mentionID, model.getAntecedentEmbedding(mentionEmbedding));
      anaphorEmbeddings.put(m.mentionID, model.getAnaphorEmbedding(mentionEmbedding));
      anaphoricityScores.incrementCount(m.mentionID,
          model.getAnaphoricityScore(mentionEmbedding,
              featureExtractor.getAnaphoricityFeatures(m, document, mentionsByHeadIndex)));
    }

    Map<Integer, List<Integer>> mentionToCandidateAntecedents = CorefUtils.heuristicFilter(sortedMentions,
        maxMentionDistance, maxMentionDistanceWithStringMatch);
    for (Map.Entry<Integer, List<Integer>> e : mentionToCandidateAntecedents.entrySet()) {
      double bestScore = anaphoricityScores.getCount(e.getKey()) - greedyness / 10.0;
      int m = e.getKey();
      Integer antecedent = null;
      for (int ca : e.getValue()) {
        double score = model.getPairwiseScore(antecedentEmbeddings.get(ca),
            anaphorEmbeddings.get(m), featureExtractor.getPairFeatures(
                  new Pair<>(ca, m), document, mentionsByHeadIndex));
        if (score > bestScore) {
          bestScore = score;
          antecedent = ca;
        }
      }

      if (antecedent != null) {
        CorefUtils.mergeCoreferenceClusters(new Pair<>(antecedent, m), document);
      }
    }
  }
}
