package edu.stanford.nlp.coref.fastneural;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.coref.CorefAlgorithm;
import edu.stanford.nlp.coref.CorefProperties;
import edu.stanford.nlp.coref.CorefUtils;
import edu.stanford.nlp.coref.data.Dictionaries;
import edu.stanford.nlp.coref.data.Document;
import edu.stanford.nlp.coref.statistical.Compressor;
import edu.stanford.nlp.coref.statistical.DocumentExamples;
import edu.stanford.nlp.coref.statistical.Example;
import edu.stanford.nlp.coref.statistical.FeatureExtractor;
import edu.stanford.nlp.coref.statistical.StatisticalCorefProperties;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.RuntimeInterruptedException;
import edu.stanford.nlp.util.logging.Redwood;
import org.ejml.simple.SimpleMatrix;

/**
 * Neural mention-ranking coreference model. Similar to the one in
 *
 * Kevin Clark and Christopher D. Manning. 2016.
 * <a href="http://nlp.stanford.edu/pubs/clark2016deep.pdf">
 * Deep Reinforcement Learning for Mention-Ranking Coreference Models</a>.
 * In Empirical Methods on Natural Language Processing.
 *
 * However, this network is smaller and trained on additional data, so it is
 * more robust to different domains and faster to run.
 *
 * @author Kevin Clark
 */
public class FastNeuralCorefAlgorithm implements CorefAlgorithm {
  private static Redwood.RedwoodChannels log = Redwood.channels(FastNeuralCorefAlgorithm.class);
  private final double greedyness;
  private final int maxMentionDistance;
  private final int maxMentionDistanceWithStringMatch;
  private final FeatureExtractor featureExtractor;
  private final FastNeuralCorefModel model;

  public FastNeuralCorefAlgorithm(Properties props, Dictionaries dictionaries) {
    greedyness = FastNeuralCorefProperties.greedyness(props);
    maxMentionDistance = CorefProperties.maxMentionDistance(props);
    maxMentionDistanceWithStringMatch = CorefProperties.maxMentionDistanceWithStringMatch(props);
    featureExtractor = new FeatureExtractor(props, dictionaries, null,
        StatisticalCorefProperties.wordCountsPath(props));
    model = IOUtils.readObjectAnnouncingTimingFromURLOrClasspathOrFileSystem(
        log, "Loading coref model...", FastNeuralCorefProperties.modelPath(props));
  }

  @Override
  public void runCoref(Document document) {
    Map<Integer, List<Integer>> mentionToCandidateAntecedents = CorefUtils.heuristicFilter(
        CorefUtils.getSortedMentions(document),
        maxMentionDistance, maxMentionDistanceWithStringMatch);
    Map<Pair<Integer, Integer>, Boolean> mentionPairs = new HashMap<>();
    for (Map.Entry<Integer, List<Integer>> e: mentionToCandidateAntecedents.entrySet()) {
      for (int m1 : e.getValue()) {
        mentionPairs.put(new Pair<>(m1, e.getKey()), true);
      }
    }

    Compressor<String> compressor = new Compressor<>();
    DocumentExamples examples = featureExtractor.extract(0, document, mentionPairs, compressor);
    Counter<Pair<Integer, Integer>> pairwiseScores = new ClassicCounter<>();
    // We cache representations for mentions so we compute them O(n) rather than O(n^2) times
    Map<Integer, SimpleMatrix> antecedentCache = new HashMap<>();
    Map<Integer, SimpleMatrix> anaphorCache = new HashMap<>();

    // Score all mention pairs on how likely they are to be coreferent
    for (Example mentionPair : examples.examples) {
      if (Thread.interrupted()) {  // Allow interrupting
        throw new RuntimeInterruptedException();
      }
      pairwiseScores.incrementCount(new Pair<>(mentionPair.mentionId1, mentionPair.mentionId2),
          model.score(
              document.predictedMentionsByID.get(mentionPair.mentionId1),
              document.predictedMentionsByID.get(mentionPair.mentionId2),
              compressor.uncompress(examples.mentionFeatures.get(mentionPair.mentionId1)),
              compressor.uncompress(examples.mentionFeatures.get(mentionPair.mentionId2)),
              compressor.uncompress(mentionPair.pairwiseFeatures),
              antecedentCache,
              anaphorCache
          ));
    }
    // Score each mention for anaphoricity
    for (int anaphorId : mentionToCandidateAntecedents.keySet()) {
      if (Thread.interrupted()) {  // Allow interrupting
        throw new RuntimeInterruptedException();
      }
      pairwiseScores.incrementCount(new Pair<>(-1, anaphorId),
          model.score(
              null,
              document.predictedMentionsByID.get(anaphorId),
              null,
              compressor.uncompress(examples.mentionFeatures.get(anaphorId)),
              null,
              antecedentCache,
              anaphorCache));
    }

    // Link each mention to the highest-scoring candidate antecedent
    for (Map.Entry<Integer, List<Integer>> e : mentionToCandidateAntecedents.entrySet()) {
      int antecedent = -1;
      int anaphor = e.getKey();
      double bestScore = pairwiseScores.getCount(new Pair<>(-1, anaphor)) - 50 * (greedyness - 0.5);
      for (int ca : e.getValue()) {
        double score = pairwiseScores.getCount(new Pair<>(ca, anaphor));
        if (score > bestScore) {
          bestScore = score;
          antecedent = ca;
        }
      }
      if (antecedent > 0) {
        CorefUtils.mergeCoreferenceClusters(new Pair<>(antecedent, anaphor), document);
      }
    }
  }
}
