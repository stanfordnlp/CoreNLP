package edu.stanford.nlp.coref.neural;

import java.io.Serializable;
import java.util.List;

import edu.stanford.nlp.neural.Embedding;
import edu.stanford.nlp.neural.NeuralUtils;
import org.ejml.simple.SimpleMatrix;

/**
 * Stores the weights and implements the matrix operations used by a {@link NeuralCorefAlgorithm}
 * @author Kevin Clark
 */
public class NeuralCorefModel implements Serializable {
  private static final long serialVersionUID = 2139427931784505653L;
  private final SimpleMatrix antecedentMatrix;
  private final SimpleMatrix anaphorMatrix;
  private final SimpleMatrix pairFeaturesMatrix;
  private final SimpleMatrix pairwiseFirstLayerBias;
  private final List<SimpleMatrix> anaphoricityModel;
  private final List<SimpleMatrix> pairwiseModel;
  private final Embedding wordEmbeddings;

  public NeuralCorefModel(SimpleMatrix antecedentMatrix, SimpleMatrix anaphorMatrix,
      SimpleMatrix pairFeaturesMatrix, SimpleMatrix pairwiseFirstLayerBias,
      List<SimpleMatrix> anaphoricityModel, List<SimpleMatrix> pairwiseModel,
      Embedding wordEmbeddings) {
    this.antecedentMatrix = antecedentMatrix;
    this.anaphorMatrix = anaphorMatrix;
    this.pairFeaturesMatrix = pairFeaturesMatrix;
    this.pairwiseFirstLayerBias = pairwiseFirstLayerBias;
    this.anaphoricityModel = anaphoricityModel;
    this.pairwiseModel = pairwiseModel;
    this.wordEmbeddings = wordEmbeddings;
  }

  public double getAnaphoricityScore(SimpleMatrix mentionEmbedding,
      SimpleMatrix anaphoricityFeatures) {
    return score(NeuralUtils.concatenate(mentionEmbedding, anaphoricityFeatures),
        anaphoricityModel);
  }

  public double getPairwiseScore(SimpleMatrix antecedentEmbedding, SimpleMatrix anaphorEmbedding,
      SimpleMatrix pairFeatures) {
    SimpleMatrix firstLayerOutput = NeuralUtils.elementwiseApplyReLU(
        antecedentEmbedding
          .plus(anaphorEmbedding)
          .plus(pairFeaturesMatrix.mult(pairFeatures))
          .plus(pairwiseFirstLayerBias));
    return score(firstLayerOutput, pairwiseModel);
  }

  private static double score(SimpleMatrix features, List<SimpleMatrix> weights) {
    for (int i = 0; i < weights.size(); i += 2) {
      features = weights.get(i).mult(features).plus(weights.get(i + 1));
      if (weights.get(i).numRows() > 1) {
        features = NeuralUtils.elementwiseApplyReLU(features);
      }
    }
    return features.elementSum();
  }

  public SimpleMatrix getAnaphorEmbedding(SimpleMatrix mentionEmbedding) {
    return anaphorMatrix.mult(mentionEmbedding);
  }

  public SimpleMatrix getAntecedentEmbedding(SimpleMatrix mentionEmbedding) {
    return antecedentMatrix.mult(mentionEmbedding);
  }

  public Embedding getWordEmbeddings() {
    return wordEmbeddings;
  }
}
