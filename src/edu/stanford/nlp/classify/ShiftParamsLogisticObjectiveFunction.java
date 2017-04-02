package edu.stanford.nlp.classify;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import edu.stanford.nlp.classify.LogPrior.LogPriorType;
import edu.stanford.nlp.optimization.AbstractCachingDiffFunction;
import edu.stanford.nlp.optimization.HasRegularizerParamRange;

/**
 * @author jtibs
 */
public class ShiftParamsLogisticObjectiveFunction extends AbstractCachingDiffFunction implements HasRegularizerParamRange {

  private final int[][] data;
  private final double[][] dataValues;
  private final int numClasses;
  private final int numFeatures;
  private final int[][] labels;
  private final int numL2Parameters;
  private final LogPrior prior;

  public ShiftParamsLogisticObjectiveFunction(int[][] data, double[][] dataValues,
      int[][] labels, int numClasses, int numFeatures, int numL2Parameters, LogPrior prior) {
    this.data = data;
    this.dataValues = dataValues;
    this.labels = labels;
    this.numClasses = numClasses;
    this.numFeatures = numFeatures;
    this.numL2Parameters = numL2Parameters;
    this.prior = prior;
  }

  @Override
  public int domainDimension() {
    return (numClasses - 1) * numFeatures;
  }

  @Override
  protected void calculate(double[] thetasArray) {
    clearResults();

    double[][] thetas = new double[numClasses - 1][numFeatures];
    LogisticUtils.unflatten(thetasArray, thetas);

    for (int i = 0; i < data.length; i++) {
      int[] featureIndices = data[i];
      double[] featureValues = dataValues[i];
      double[] sums = LogisticUtils.calculateSums(thetas, featureIndices, featureValues);

      for (int c = 0; c < numClasses; c++) {
        double sum = sums[c];
        value -= sum * labels[i][c];

        if (c == 0) continue;
        int offset = (c - 1) * numFeatures;
        double error = Math.exp(sum) - labels[i][c];
        for (int f = 0; f < featureIndices.length; f++) {
          int index = featureIndices[f];
          double x = featureValues[f];
          derivative[offset + index] -= error * x;
        }
      }
    }

    // incorporate prior
    if (prior.getType().equals(LogPriorType.NULL)) return;
    double sigma = prior.getSigma();

    for (int c = 0; c < numClasses; c++) {
      if (c == 0) continue;
      int offset = (c - 1) * numFeatures;

      for (int j = 0; j < numL2Parameters; j++) {
        double theta = thetasArray[offset + j];
        value += theta * theta / (sigma * 2.0);
        derivative[offset + j] += theta / sigma;
      }
    }
  }

  private void clearResults() {
    value = 0.0;
    Arrays.fill(derivative, 0.0);
  }

  @Override
  public Set<Integer> getRegularizerParamRange(double[] x) {
    Set<Integer> result = new HashSet<>();
    for (int i = numL2Parameters; i < x.length; i++)
      result.add(i);
    return result;
  }

}
