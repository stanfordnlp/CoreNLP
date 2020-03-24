package edu.stanford.nlp.coref.fastneural;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.ejml.simple.SimpleMatrix;
import edu.stanford.nlp.coref.CorefProperties;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.coref.neural.CategoricalFeatureExtractor;
import edu.stanford.nlp.coref.neural.EmbeddingExtractor;
import edu.stanford.nlp.coref.neural.NeuralCorefProperties;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.neural.NeuralUtils;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Featurizes and scores mention pairs using {@link FastNeuralCorefNetwork}.
 * @author kevinclark
 */
public class FastNeuralCorefModel {
  private final EmbeddingExtractor embeddingExtractor;
  private final FastNeuralCorefNetwork network;
  private final Map<String, Integer> pairFeatureIds;
  private final Map<String, Integer> mentionFeatureIds;

  public FastNeuralCorefModel(EmbeddingExtractor embeddingExtractor, FastNeuralCorefNetwork network,
      Map<String, Integer> pairFeatureIds, Map<String, Integer> mentionFeatureIds) {
    this.embeddingExtractor = embeddingExtractor;
    this.network = network;
    this.pairFeatureIds = pairFeatureIds;
    this.mentionFeatureIds = mentionFeatureIds;
  }

  public double score(Mention antecedent, Mention anaphor, Counter<String> antecedentFeatures,
      Counter<String> anaphorFeatures, Counter<String> pairFeatures) {
    MentionPairVectors mentionPair = new MentionPairVectors(
        antecedent == null ? null : embeddingExtractor.getMentionEmbeddingsForFast(antecedent),
        embeddingExtractor.getMentionEmbeddingsForFast(anaphor),
        antecedent == null ? null : makeFeatureVector(antecedentFeatures, mentionFeatureIds),
        makeFeatureVector(anaphorFeatures, mentionFeatureIds),
        pairFeatures == null ? new SimpleMatrix(pairFeatureIds.size() + 23, 1) :
          addDistanceFeatures(makeFeatureVector(pairFeatures, pairFeatureIds),
          antecedent, anaphor));
    return network.score(mentionPair);
  }

  private SimpleMatrix makeFeatureVector(Counter<String> features, Map<String, Integer> featureIds) {
    SimpleMatrix featureVector = new SimpleMatrix(featureIds.size(), 1);
    for (Map.Entry<String, Double> feature : features.entrySet()) {
      if (featureIds.containsKey(feature.getKey())) {
        featureVector.set(featureIds.get(feature.getKey()), feature.getValue());
      }
    }
    return featureVector;
  }

  private SimpleMatrix addDistanceFeatures(SimpleMatrix featureVector,
      Mention antecedent, Mention anaphor) {
    return NeuralUtils.concatenate(
        featureVector,
        CategoricalFeatureExtractor.encodeDistance(
            anaphor.sentNum - antecedent.sentNum),
        CategoricalFeatureExtractor.encodeDistance(
            anaphor.mentionNum - antecedent.mentionNum - 1),
        new SimpleMatrix(new double[][] {{
          antecedent.sentNum == anaphor.sentNum &&
              antecedent.endIndex > anaphor.startIndex ? 1 : 0}})
    );
  }

  public static FastNeuralCorefModel getDummyNeuralCorefModel(Properties props, Redwood.RedwoodChannels log) {
    Map<String, Integer> mentionFeatureIds = new HashMap<>();
    Map<String, Integer> pairFeatureIds = new HashMap<>();
    for (int i = 0; i < 100; i++) {
      mentionFeatureIds.put(String.valueOf(i), i);
    }
    for (int i = 0; i < 211; i++) {
      pairFeatureIds.put(String.valueOf(i), i);
    }
    List<SimpleMatrix> weights = Arrays.asList(new SimpleMatrix[] {
        new SimpleMatrix(128, 450),  //Ana
        new SimpleMatrix(128, 1),
        new SimpleMatrix(128, 450),  //Ant
        new SimpleMatrix(128, 1),
        new SimpleMatrix(128, 234),  //Pair
        new SimpleMatrix(128, 1),
        new SimpleMatrix(128, 1),  //NA
        new SimpleMatrix(128, 128),   // hidden
        new SimpleMatrix(128, 1),
        new SimpleMatrix(128, 128),
        new SimpleMatrix(128, 1),
        new SimpleMatrix(1, 128),  // logits
        new SimpleMatrix(1, 1),
    });

    EmbeddingExtractor embeddingExtractor = new EmbeddingExtractor(CorefProperties.conll(props), null,
        IOUtils.readObjectAnnouncingTimingFromURLOrClasspathOrFileSystem(
            log, "Loading coref embeddings", NeuralCorefProperties.pretrainedEmbeddingsPath(props)));
    FastNeuralCorefNetwork network = new FastNeuralCorefNetwork(weights);
    return new FastNeuralCorefModel(embeddingExtractor, network, pairFeatureIds, mentionFeatureIds);
  }
}
