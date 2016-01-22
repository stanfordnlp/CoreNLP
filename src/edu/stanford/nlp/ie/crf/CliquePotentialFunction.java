package edu.stanford.nlp.ie.crf;

/**
 * @author Mengqiu Wang
 */
@FunctionalInterface
public interface CliquePotentialFunction {

  /**
   * @param cliqueSize 1 if node clique, 2 if edge clique, etc
   * @param labelIndex the index of the output class label
   * @param cliqueFeatures an int array containing the feature indices that are active in this clique
   * @param featureVal a double array containing the feature values corresponding to feature indices in cliqueFeatures
   *
   * @return clique potential value
   */
  public double computeCliquePotential(int cliqueSize, int labelIndex,
    int[] cliqueFeatures, double[] featureVal, int posInSent);

}
