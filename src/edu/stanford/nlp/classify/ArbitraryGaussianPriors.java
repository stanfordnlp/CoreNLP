package edu.stanford.nlp.classify;

import edu.stanford.nlp.util.Pair;

import java.util.ArrayList;

/** 
 * An array of GaussianPriors with arbitrary size for storing means and variances of a bunch of features.
 *
 *  @author kristina@cs.stanford.edu
 */
class ArbitraryGaussianPriors implements GaussianPriors {

  private ArrayList<Pair<Double, Double>> parameters;

  ArbitraryGaussianPriors(double sigma, double mean, int size) {
    //make a generic one with the same parameters
    parameters = new ArrayList<Pair<Double, Double>>();
    Pair<Double, Double> p = new Pair<Double, Double>(sigma, mean);
    for (int i = 0; i < size; i++) {
      parameters.add(p);
    }
  }

  void setSpecial(int fIndex, double sigma, double mean) {
    parameters.set(fIndex, new Pair<Double, Double>(sigma, mean));
  }

  public double sigmaSq(int fIndex) {
    double sigma = parameters.get(fIndex).first();
    return sigma * sigma;
  }

  public double sigma(int fIndex) {
    return parameters.get(fIndex).first();
  }

  public double mean(int fIndex) {
    return parameters.get(fIndex).second();
  }
  
}
