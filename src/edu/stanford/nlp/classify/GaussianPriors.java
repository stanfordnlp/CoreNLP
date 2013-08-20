package edu.stanford.nlp.classify;

/** An interface for storing means and variances of a bunch of features.
 *
 *  @author kristina@cs.stanford.edu
 */
public interface GaussianPriors {

  public double sigma(int fIndex);

  public double mean(int fIndex);

  public double sigmaSq(int fIndex);
  
}
