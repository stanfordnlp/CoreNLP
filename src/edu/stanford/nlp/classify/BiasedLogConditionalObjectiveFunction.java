package edu.stanford.nlp.classify;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.optimization.AbstractCachingDiffFunction;

import java.util.Arrays;


/**
 * Maximizes the conditional likelihood with a given prior.
 *
 * @author Jenny Finkel
 */

public class BiasedLogConditionalObjectiveFunction extends AbstractCachingDiffFunction {

  public void setPrior(LogPrior prior) {
    this.prior = prior;
  }

  protected LogPrior prior;

  protected int numFeatures = 0;
  protected int numClasses = 0;

  protected int[][] data = null;
  protected int[] labels = null;

  private double[][] confusionMatrix;
  
  @Override
  public int domainDimension() {
    return numFeatures * numClasses;
  }

  int classOf(int index) {
    return index % numClasses;
  }

  int featureOf(int index) {
    return index / numClasses;
  }

  protected int indexOf(int f, int c) {
    return f * numClasses + c;
  }

  public double[][] to2D(double[] x) {
    double[][] x2 = new double[numFeatures][numClasses];
    for (int i = 0; i < numFeatures; i++) {
      for (int j = 0; j < numClasses; j++) {
        x2[i][j] = x[indexOf(i, j)];
      }
    }
    return x2;
  }

  @Override
  protected void calculate(double[] x) {
    
    if (derivative == null) {
      derivative = new double[x.length];
    } else {
      Arrays.fill(derivative, 0.0);
    }

    value = 0.0;

    double[] sums = new double[numClasses];
    double[] probs = new double[numClasses];
    double[] weightedProbs = new double[numClasses];

    for (int d = 0; d < data.length; d++) {
      int[] features = data[d];
      int observedLabel = labels[d];
      // activation
      Arrays.fill(sums, 0.0);

      for (int c = 0; c < numClasses; c++) {
        for (int f = 0; f < features.length; f++) {
          int i = indexOf(features[f], c);
          sums[c] += x[i];
        }
      }

      double total = ArrayMath.logSum(sums);

      double[] weightedSums = new double[numClasses];
      for (int trueLabel = 0; trueLabel < numClasses; trueLabel++) {
        weightedSums[trueLabel] = Math.log(confusionMatrix[observedLabel][trueLabel]) + sums[trueLabel];
      }

      double weightedTotal = ArrayMath.logSum(weightedSums);
      
      for (int c = 0; c < numClasses; c++) {
        probs[c] = Math.exp(sums[c] - total);
        weightedProbs[c] = Math.exp(weightedSums[c] - weightedTotal);
        for (int f = 0; f < features.length; f++) {
          int i = indexOf(features[f], c);
          derivative[i] += probs[c] - weightedProbs[c];
        }
      }

      double tmpValue = 0.0;
      for (int c = 0; c < numClasses; c++) {
        tmpValue += confusionMatrix[observedLabel][c] * Math.exp(sums[c] - total);
      }
      value -= Math.log(tmpValue);
    }
    
    value += prior.compute(x, derivative);
    
  }



  public BiasedLogConditionalObjectiveFunction(GeneralDataset<?, ?> dataset, double[][] confusionMatrix) {
    this(dataset, confusionMatrix, new LogPrior(LogPrior.LogPriorType.QUADRATIC));
  }

  public BiasedLogConditionalObjectiveFunction(GeneralDataset<?, ?> dataset, double[][] confusionMatrix, LogPrior prior) {
    this(dataset.numFeatures(), dataset.numClasses(), dataset.getDataArray(), dataset.getLabelsArray(), confusionMatrix, prior);
  }

  public BiasedLogConditionalObjectiveFunction(int numFeatures, int numClasses, int[][] data, int[] labels, double[][] confusionMatrix) {
    this(numFeatures, numClasses, data, labels, confusionMatrix, new LogPrior(LogPrior.LogPriorType.QUADRATIC));
  }

  public BiasedLogConditionalObjectiveFunction(int numFeatures, int numClasses, int[][] data, int[] labels, double[][] confusionMatrix, LogPrior prior) {
    this.numFeatures = numFeatures;
    this.numClasses = numClasses;
    this.data = data;
    this.labels = labels;
    this.prior = prior;
    this.confusionMatrix = confusionMatrix;
  }
}
