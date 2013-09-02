package edu.stanford.nlp.classify;

/** An Constant GaussianPrior for storing mean and variance of one feature.
 *
 *  @author kristina@cs.stanford.edu
 */
class ConstantGaussianPriors implements GaussianPriors {
  double sigma;
  double mean;
  double sigmaSq;

  public ConstantGaussianPriors(double sigma, double mean) {
    this.sigma = sigma;
    sigmaSq = sigma * sigma;
    this.mean = mean;
  }

  public double sigma(int fIndex) {
    return sigma;
  }

  public double sigmaSq(int fIndex) {
    return sigmaSq;
  }

  public double mean(int fIndex) {
    return mean;
  }
}
