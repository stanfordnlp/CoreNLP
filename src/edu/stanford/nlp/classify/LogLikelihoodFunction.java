package edu.stanford.nlp.classify;

/**
 * Just a name for a differential log-likelihood
 */
public interface LogLikelihoodFunction {
  public double logLikelihood(double[] weights);

  public double[] gradient(double[] weights);

  public int domainDimension();
}
