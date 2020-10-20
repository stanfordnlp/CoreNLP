package edu.stanford.nlp.classify;

/**
 * Creates a classifier with given weights
 *
 * @author Angel Chang
 */
public interface ClassifierCreator<L,F> {
  public Classifier<L,F> createClassifier(double[] weights);
}
