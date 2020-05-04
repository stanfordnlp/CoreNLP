package edu.stanford.nlp.ie.crf;

/**
 * @author Mengqiu Wang
 */
public class LinearCliquePotentialFunction implements CliquePotentialFunction {

  private final float[][] fWeights;
  private final double[][] dWeights;

  LinearCliquePotentialFunction(float[][] weights) {
    this.fWeights = weights;
    this.dWeights = null;
  }

  LinearCliquePotentialFunction(double[][] weights) {
    this.fWeights = null;
    this.dWeights = weights;
  }

  @Override
  public double computeCliquePotential(int cliqueSize, int labelIndex,
                                       int[] cliqueFeatures, double[] featureVal, int posInSent) {
    double output = 0.0;
    for (int m = 0; m < cliqueFeatures.length; m++) {
      double dotProd = fWeights == null ? dWeights[cliqueFeatures[m]][labelIndex] : fWeights[cliqueFeatures[m]][labelIndex];
      if (featureVal != null) {
        dotProd *= featureVal[m];
      }
      output += dotProd;
    }
    return output;
  }

}
