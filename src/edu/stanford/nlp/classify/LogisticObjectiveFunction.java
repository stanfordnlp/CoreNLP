package edu.stanford.nlp.classify;

import edu.stanford.nlp.optimization.AbstractCachingDiffFunction;

import java.util.Arrays;


/**
 * Maximizes the conditional likelihood with a given prior.
 * Because the problem is binary, optimizations are possible that
 * cannot be done in LogConditionalObjectiveFunction.
 *
 * @author Galen Andrew
 */

public class LogisticObjectiveFunction extends AbstractCachingDiffFunction {

  private final int numFeatures;
  private final int[][] data;
  private final double[][] dataValues; 
  private final int[] labels;
  protected float[] dataweights = null;
  private final LogPrior prior;
  

  @Override
  public int domainDimension() {
    return numFeatures;
  }

  @Override
  protected void calculate(double[] x) {

    if (dataValues != null) {
      calculateRVF(x);
      return;
    }

    value = 0.0;
    Arrays.fill(derivative, 0.0);

    for (int d = 0; d < data.length; d++) {
      int[] features = data[d];
      double sum = 0;

      for (int feature1 : features) {
        sum += x[feature1];
      }

      double expSum, derivativeIncrement;

      if (labels[d] == 0) {
        expSum = Math.exp(sum);
        derivativeIncrement = 1.0 / (1.0 + (1.0 / expSum));
      } else {
        expSum = Math.exp(-sum);
        derivativeIncrement = -1.0 / (1.0 + (1.0 / expSum));
      }

      if (dataweights == null) {
        value += Math.log(1.0 + expSum);
      } else {
        value += Math.log(1.0 + expSum) * dataweights[d];
        derivativeIncrement *= dataweights[d];
      }

      for (int feature : features) {
        derivative[feature] += derivativeIncrement;
      }
    }

    value += prior.compute(x, derivative);
  }

  protected void calculateRVF(double[] x) {

    value = 0.0;
    Arrays.fill(derivative, 0.0);

    for (int d = 0; d < data.length; d++) {
      int[] features = data[d];
      double[] values = dataValues[d];
      double sum = 0;

      for (int f = 0; f < features.length; f++) {
        sum += x[features[f]]*values[f];
      }

      double expSum, derivativeIncrement;

      if (labels[d] == 0) {
        expSum = Math.exp(sum);
        derivativeIncrement = 1.0 / (1.0 + (1.0 / expSum));
      } else {
        expSum = Math.exp(-sum);
        derivativeIncrement = -1.0 / (1.0 + (1.0 / expSum));
      }

      if (dataweights == null) {
        value += Math.log(1.0 + expSum);
      } else {
        value += Math.log(1.0 + expSum) * dataweights[d];
        derivativeIncrement *= dataweights[d];
      }

      for (int f = 0; f < features.length; f++) {
        derivative[features[f]] += values[f]*derivativeIncrement;
      }
    }

    value += prior.compute(x, derivative);
  }


  public LogisticObjectiveFunction(int numFeatures, int[][] data, int[] labels) {
    this(numFeatures, data, labels, new LogPrior(LogPrior.LogPriorType.QUADRATIC));
  }

  public LogisticObjectiveFunction(int numFeatures, int[][] data, int[] labels, LogPrior prior) {
    this(numFeatures, data, labels, prior, null);
  }

  public LogisticObjectiveFunction(int numFeatures, int[][] data, int[] labels, float[] dataweights) {
    this(numFeatures, data, labels, new LogPrior(LogPrior.LogPriorType.QUADRATIC), dataweights);
  }
  public LogisticObjectiveFunction(int numFeatures, int[][] data, int[] labels, LogPrior prior, float[] dataweights) {
    this(numFeatures, data, null, labels, prior, dataweights);
  }

  public LogisticObjectiveFunction(int numFeatures, int[][] data, double[][] values, int[] labels) {
    this(numFeatures, data, values, labels, new LogPrior(LogPrior.LogPriorType.QUADRATIC));
  }

  public LogisticObjectiveFunction(int numFeatures, int[][] data, double[][] values, int[] labels, LogPrior prior) {
    this(numFeatures, data, values, labels, prior, null);
  }
  
  public LogisticObjectiveFunction(int numFeatures, int[][] data, double[][] values, int[] labels, float[] dataweights) {
    this(numFeatures, data, values, labels, new LogPrior(LogPrior.LogPriorType.QUADRATIC), dataweights);
  }

  public LogisticObjectiveFunction(int numFeatures, int[][] data, double[][] values, int[] labels, LogPrior prior, float[] dataweights) {
    this.numFeatures = numFeatures;
    this.data = data;
    this.labels = labels;
    this.prior = prior;
    this.dataweights = dataweights;
    this.dataValues = values;
  }
}
