package edu.stanford.nlp.coref.fastneural;

import org.ejml.simple.SimpleMatrix;

public class MentionPairVectors {
  public SimpleMatrix antecedentEmbedding;
  public SimpleMatrix anaphorEmbedding;
  public SimpleMatrix antecedentFeatures;
  public SimpleMatrix anaphorFeatures;
  public SimpleMatrix pairFeatures;

  public MentionPairVectors(SimpleMatrix antecedentEmbedding, SimpleMatrix anaphorEmbedding,
      SimpleMatrix antecedentFeatures, SimpleMatrix anaphorFeatures, SimpleMatrix pairFeatures) {
    this.antecedentEmbedding = antecedentEmbedding;
    this.anaphorEmbedding = anaphorEmbedding;
    this.antecedentFeatures = antecedentFeatures;
    this.anaphorFeatures = anaphorFeatures;
    this.pairFeatures = pairFeatures;
  }
}
