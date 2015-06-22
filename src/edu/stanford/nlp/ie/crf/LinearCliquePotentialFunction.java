package edu.stanford.nlp.ie.crf;

/**
 * @author Mengqiu Wang
 */

public class LinearCliquePotentialFunction implements CliquePotentialFunction {

  double[][] weights;

  LinearCliquePotentialFunction(double[][] weights) {
    this.weights = weights;
  }

  @Override
  public double computeCliquePotential(int cliqueSize, int labelIndex,
      int[] cliqueFeatures, double[] featureVal, int posInSent) {
    double output = 0.0;
    double dotProd = 0;
    for (int m = 0; m < cliqueFeatures.length; m++) {
      dotProd = weights[cliqueFeatures[m]][labelIndex];
      if (featureVal != null)
        dotProd *= featureVal[m];
      output += dotProd;
    }
    return output;
  }
}
