package edu.stanford.nlp.coref.fastneural;

import java.io.Serializable;
import java.util.List;

import edu.stanford.nlp.neural.NeuralUtils;
import org.ejml.simple.SimpleMatrix;

/**
 * Stores the weights and implements the matrix operations used by a
 * {@link FastNeuralCorefAlgorithm} to score mention pairs.
 * @author Kevin Clark
 */
public class FastNeuralCorefNetwork implements Serializable {
  private static final long serialVersionUID = -6781048745730605235L;

  private SimpleMatrix anaphorKernel;
  private SimpleMatrix anaphorBias;
  private SimpleMatrix antecedentKernel;
  private SimpleMatrix antecedentBias;
  private SimpleMatrix pairFeaturesKernel;
  private SimpleMatrix pairFeaturesBias;
  private SimpleMatrix NARepresentation;
  private List<SimpleMatrix> networkLayers;

  public FastNeuralCorefNetwork(List<SimpleMatrix> weights) {
    anaphorKernel = weights.get(0);
    anaphorBias = weights.get(1);
    antecedentKernel = weights.get(2);
    antecedentBias = weights.get(3);
    pairFeaturesKernel = weights.get(4);
    pairFeaturesBias = weights.get(5);
    NARepresentation = weights.get(6);
    networkLayers = weights.subList(7, weights.size());
  }

  public double score(MentionPairVectors mentionPair) {
    SimpleMatrix antecedentVector = null;
    if (mentionPair.antecedentEmbedding == null) {
      antecedentVector = NARepresentation;
    } else {
      antecedentVector = antecedentKernel
        .mult(NeuralUtils.concatenate(
            mentionPair.antecedentEmbedding, mentionPair.antecedentFeatures))
        .plus(antecedentBias);
    }
    SimpleMatrix anaphorVector = anaphorKernel
        .mult(NeuralUtils.concatenate(
            mentionPair.anaphorEmbedding, mentionPair.anaphorFeatures))
        .plus(anaphorBias);
    SimpleMatrix pairFeaturesVector = pairFeaturesKernel
        .mult(mentionPair.pairFeatures).plus(pairFeaturesBias);
    SimpleMatrix pairVector = antecedentVector.plus(anaphorVector).plus(pairFeaturesVector);
    for (int i = 0; i < networkLayers.size(); i += 2) {
      pairVector = networkLayers.get(i).mult(pairVector).plus(networkLayers.get(i + 1));
      if (networkLayers.get(i).numRows() > 1) {
        pairVector = NeuralUtils.elementwiseApplyReLU(pairVector);
      }
    }
    return pairVector.elementSum();
  }
}
