package edu.stanford.nlp.ie.crf;

/**
 * Indicates that this function can build a clique potential function for external use
 *
 * @author Mengqiu Wang
 */
public interface HasCliquePotentialFunction {
  public CliquePotentialFunction getCliquePotentialFunction(double[] x);
}
