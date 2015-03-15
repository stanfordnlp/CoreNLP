package edu.stanford.nlp.ie.crf;

/**
 * @author Mengqiu Wang
 */

public class NoisyLabelLinearCliquePotentialFunction implements CliquePotentialFunction {

  double[][] weights;
  int[] docLabels;
  double[][] errorMatrix;

  public NoisyLabelLinearCliquePotentialFunction(double[][] weights, int[] docLabels, double[][] errorMatrix) {
    this.weights = weights;
    this.docLabels = docLabels;
    this.errorMatrix = errorMatrix;
  }

  private double g(int labelIndex, int posInSent) {
    if (errorMatrix == null)
      return 0;
    int observed = docLabels[posInSent];
    int truth = labelIndex;
    return errorMatrix[truth][observed];
  }

  @Override
  public double computeCliquePotential(int cliqueSize, int labelIndex, int[] cliqueFeatures,
      double[] featureVal, int posInSent) {
    double output = 0.0;
    double dotProd = 0;
    for (int m = 0; m < cliqueFeatures.length; m++) {
      dotProd = weights[cliqueFeatures[m]][labelIndex];
      output += dotProd;
    }
    if (cliqueSize == 1) { // add the noisy label part
      output += g(labelIndex, posInSent);
    }
    return output;
  }
}
