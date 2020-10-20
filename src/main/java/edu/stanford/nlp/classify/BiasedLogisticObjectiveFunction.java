package edu.stanford.nlp.classify;

import edu.stanford.nlp.optimization.AbstractCachingDiffFunction;

import java.util.Arrays;


/**
 * @author jrfinkel
 */
public class BiasedLogisticObjectiveFunction extends AbstractCachingDiffFunction {

  private final int numFeatures;
  private final int[][] data;
  private final double[][] dataValues;
  private final int[] labels;
  protected float[] dataweights = null;
  private final LogPrior prior;
  double probCorrect = 0.7;

  @Override
  public int domainDimension() {
    return numFeatures;
  }

  @Override
  protected void calculate(double[] x) {

    if (dataValues != null) {
      throw new RuntimeException();
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

      if (dataweights != null) {
        throw new RuntimeException();
      }

      if (labels[d] == 1) {
        expSum = Math.exp(-sum);
        double g = (1 / (1 + expSum));
        value -= Math.log(g);
        derivativeIncrement = (g-1);
      } else {
//         expSum = Math.exp(-sum);
//         double g = (1 / (1 + expSum));
//         value -= Math.log(1-g);
//         derivativeIncrement = (g);
//       }
        expSum = Math.exp(-sum);
        double g = (1 / (1 + expSum));
        double e = (1-probCorrect) * g + (probCorrect)*(1 - g);
        value -= Math.log(e);
        derivativeIncrement = -(g*(1-g)*(1-2*probCorrect)) / (e);
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

      for (int feature1 : features) {
        sum += x[feature1] * values[feature1];
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
        derivative[feature] += values[feature] * derivativeIncrement;
      }
    }

    value += prior.compute(x, derivative);
  }


  public BiasedLogisticObjectiveFunction(int numFeatures, int[][] data, int[] labels) {
    this(numFeatures, data, labels, new LogPrior(LogPrior.LogPriorType.QUADRATIC));
  }

  public BiasedLogisticObjectiveFunction(int numFeatures, int[][] data, int[] labels, LogPrior prior) {
    this(numFeatures, data, labels, prior, null);
  }

  public BiasedLogisticObjectiveFunction(int numFeatures, int[][] data, int[] labels, float[] dataweights) {
    this(numFeatures, data, labels, new LogPrior(LogPrior.LogPriorType.QUADRATIC), dataweights);
  }
  public BiasedLogisticObjectiveFunction(int numFeatures, int[][] data, int[] labels, LogPrior prior, float[] dataweights) {
    this(numFeatures, data, null, labels, prior, dataweights);
  }

  public BiasedLogisticObjectiveFunction(int numFeatures, int[][] data, double[][] values, int[] labels) {
    this(numFeatures, data, values, labels, new LogPrior(LogPrior.LogPriorType.QUADRATIC));
  }

  public BiasedLogisticObjectiveFunction(int numFeatures, int[][] data, double[][] values, int[] labels, LogPrior prior) {
    this(numFeatures, data, values, labels, prior, null);
  }

  public BiasedLogisticObjectiveFunction(int numFeatures, int[][] data, double[][] values, int[] labels, LogPrior prior, float[] dataweights) {
    this.numFeatures = numFeatures;
    this.data = data;
    this.labels = labels;
    this.prior = prior;
    this.dataweights = dataweights;
    this.dataValues = values;
  }
}
