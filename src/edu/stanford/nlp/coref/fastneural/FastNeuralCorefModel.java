package edu.stanford.nlp.coref.fastneural;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.coref.neural.CategoricalFeatureExtractor;
import edu.stanford.nlp.coref.neural.EmbeddingExtractor;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.neural.Embedding;
import edu.stanford.nlp.neural.NeuralUtils;
import edu.stanford.nlp.stats.Counter;
import org.ejml.simple.SimpleMatrix;

/**
 * Featurizes and scores mention pairs using a neural network.
 * @author kevinclark
 */
public class FastNeuralCorefModel implements Serializable {
  private static final long serialVersionUID = 8663264823377059140L;

  private final EmbeddingExtractor embeddingExtractor;
  private final Map<String, Integer> pairFeatureIds;
  private final Map<String, Integer> mentionFeatureIds;

  private SimpleMatrix anaphorKernel;
  private SimpleMatrix anaphorBias;
  private SimpleMatrix antecedentKernel;
  private SimpleMatrix antecedentBias;
  private SimpleMatrix pairFeaturesKernel;
  private SimpleMatrix pairFeaturesBias;
  private SimpleMatrix NARepresentation;
  private List<SimpleMatrix> networkLayers;

  public FastNeuralCorefModel(EmbeddingExtractor embeddingExtractor,
                              Map<String, Integer> pairFeatureIds,
                              Map<String, Integer> mentionFeatureIds,
                              List<SimpleMatrix> weights) {
    this.embeddingExtractor = embeddingExtractor;
    this.pairFeatureIds = pairFeatureIds;
    this.mentionFeatureIds = mentionFeatureIds;

    anaphorKernel = weights.get(0);
    anaphorBias = weights.get(1);
    antecedentKernel = weights.get(2);
    antecedentBias = weights.get(3);
    pairFeaturesKernel = weights.get(4);
    pairFeaturesBias = weights.get(5);
    NARepresentation = weights.get(6);
    networkLayers = new ArrayList<>(weights.subList(7, weights.size()));
  }

  public EmbeddingExtractor getEmbeddingExtractor() {
    return embeddingExtractor;
  }

  public Map<String, Integer> getPairFeatureIds() {
    return Collections.unmodifiableMap(pairFeatureIds);
  }

  public Map<String, Integer> getMentionFeatureIds() {
    return Collections.unmodifiableMap(mentionFeatureIds);
  }

  public List<SimpleMatrix> getAllWeights() {
    List<SimpleMatrix> weights = new ArrayList<>();
    weights.add(anaphorKernel);
    weights.add(anaphorBias);
    weights.add(antecedentKernel);
    weights.add(anaphorBias);
    weights.add(pairFeaturesKernel);
    weights.add(pairFeaturesBias);
    weights.add(NARepresentation);
    weights.addAll(networkLayers);
    return Collections.unmodifiableList(weights);
  }

  public double score(Mention antecedent, Mention anaphor, Counter<String> antecedentFeatures,
      Counter<String> anaphorFeatures, Counter<String> pairFeatures,
      Map<Integer, SimpleMatrix> antecedentCache, Map<Integer, SimpleMatrix> anaphorCache) {
    SimpleMatrix antecedentVector = NARepresentation;
    if (antecedent != null) {
      antecedentVector = antecedentCache.get(antecedent.mentionID);
      if (antecedentVector == null) {
        antecedentVector = antecedentKernel
            .mult(NeuralUtils.concatenate(
                embeddingExtractor.getMentionEmbeddingsForFast(antecedent),
                makeFeatureVector(antecedentFeatures, mentionFeatureIds)))
            .plus(antecedentBias);
        antecedentCache.put(antecedent.mentionID, antecedentVector);
      }
    }
    SimpleMatrix anaphorVector = anaphorCache.get(anaphor.mentionID);
    if (anaphorVector == null) {
      anaphorVector = anaphorKernel
          .mult(NeuralUtils.concatenate(
              embeddingExtractor.getMentionEmbeddingsForFast(anaphor),
              makeFeatureVector(anaphorFeatures, mentionFeatureIds)))
          .plus(anaphorBias);
      anaphorCache.put(anaphor.mentionID, anaphorVector);
    }
    SimpleMatrix pairFeaturesVector = pairFeaturesKernel
        .mult(pairFeatures == null ? new SimpleMatrix(pairFeatureIds.size() + 23, 1) :
          addDistanceFeatures(
              makeFeatureVector(pairFeatures, pairFeatureIds), antecedent, anaphor))
        .plus(pairFeaturesBias);
    SimpleMatrix pairVector = antecedentVector.concatRows(anaphorVector).concatRows(
        pairFeaturesVector);
    pairVector = NeuralUtils.elementwiseApplyReLU(pairVector);
    for (int i = 0; i < networkLayers.size(); i += 2) {
      pairVector = networkLayers.get(i).mult(pairVector).plus(networkLayers.get(i + 1));
      if (networkLayers.get(i).numRows() > 1) {
        pairVector = NeuralUtils.elementwiseApplyReLU(pairVector);
      }
    }
    return pairVector.elementSum();
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

  public static FastNeuralCorefModel loadFromTextFiles(String path) {
    List<SimpleMatrix> weights = NeuralUtils.loadTextMatrices(path + "weights.txt");
    weights.set(weights.size() - 2, weights.get(weights.size() - 2).transpose());
    Embedding embeddings = new Embedding(path + "embeddings.txt");
    EmbeddingExtractor extractor = new EmbeddingExtractor(false, null, embeddings, "<missing>");
    Map<String, Integer> pairFeatureIds = loadMapFromTextFile(path + "pair_features.txt");
    Map<String, Integer> mentionFeatureIds = loadMapFromTextFile(path + "mention_features.txt");
    return new FastNeuralCorefModel(extractor, pairFeatureIds, mentionFeatureIds, weights);
  }

  public static Map<String, Integer> loadMapFromTextFile(String filename) {
    Map<String, Integer> dict = new HashMap<>();
    for (String line : IOUtils.readLines(filename, "utf-8")) {
      String[] lineSplit = line.split("\\s+");
      assert lineSplit.length == 2;
      dict.put(lineSplit[0], Integer.parseInt(lineSplit[1]));
    }
    return dict;
  }
}
