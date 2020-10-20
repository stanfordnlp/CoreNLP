package edu.stanford.nlp.classify;

/**
 * Creates a probablic classifier with given weights
 *
 * @author Angel Chang
 */
public interface ProbabilisticClassifierCreator<L,F> {
  public ProbabilisticClassifier<L,F> createProbabilisticClassifier(double[] weights);
}