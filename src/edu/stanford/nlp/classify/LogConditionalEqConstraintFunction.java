package edu.stanford.nlp.classify;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.optimization.AbstractCachingDiffFunction;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.IntTriple;
import edu.stanford.nlp.util.IntTuple;
import edu.stanford.nlp.util.IntUni;

import java.util.Arrays;

/**
 * Maximizes the conditional likelihood with a given prior.
 * Constrains parameters for the same history to sum to 1
 * Adapted from {@link LogConditionalObjectiveFunction}
 *
 * @author Kristina Toutanova
 */

public class LogConditionalEqConstraintFunction extends AbstractCachingDiffFunction {

  public static final int NO_PRIOR = 0;
  public static final int QUADRATIC_PRIOR = 1;
  /* Use a Huber robust regression penalty (L1 except very near 0) not L2 */
  public static final int HUBER_PRIOR = 2;
  public static final int QUARTIC_PRIOR = 3;

  protected int numFeatures = 0;
  protected int numClasses = 0;

  protected int[][] data = null;
  protected int[] labels = null;
  protected int[] numValues = null;
  private int prior;
  private double sigma = 1.0;
  private double epsilon;
  private Index<IntTuple> featureIndex;

  @Override
  public int domainDimension() {
    return featureIndex.size();
  }

  int classOf(int index) {
    IntTuple i = featureIndex.get(index);
    return i.get(0);

  }

  /**
   * the feature number of the original feature or -1 if this is for a prior
   *
   */
  int featureOf(int index) {
    IntTuple i = featureIndex.get(index);
    if (i.length() == 1) {
      return -1;
    }
    return i.get(1);
  }

  /**
   * @return the index of the prior for class c
   */
  protected int indexOf(int c) {
    return featureIndex.indexOf(new IntUni(c));
  }

  protected int indexOf(int f, int c, int val) {
    return featureIndex.indexOf(new IntTriple(c, f, val));
  }

  /**
   * create an index for each parameter - the prior probs and the features with all of their values
   *
   */
  protected Index<IntTuple> createIndex() {
    Index<IntTuple> index = new HashIndex<>();
    for (int c = 0; c < numClasses; c++) {
      index.add(new IntUni(c));
      for (int f = 0; f < numFeatures; f++) {
        for (int val = 0; val < numValues[f]; val++) {
          index.add(new IntTriple(c, f, val));
        }
      }
    }

    return index;
  }

  public double[][][] to3D(double[] x1) {
    double[] x = normalize(x1);
    double[][][] x2 = new double[numClasses][numFeatures][];
    for (int c = 0; c < numClasses; c++) {
      for (int f = 0; f < numFeatures; f++) {
        x2[c][f] = new double[numValues[f]];
        for (int val = 0; val < numValues[f]; val++) {
          x2[c][f][val] = x[indexOf(f, c, val)];
        }
      }
    }
    return x2;
  }

  public double[] priors(double[] x1) {
    double[] x = normalize(x1);
    double[] x2 = new double[numClasses];
    for (int c = 0; c < numClasses; c++) {
      x2[c] = x[indexOf(c)];
    }
    return x2;
  }

  /**
   * normalize the parameters s.t qi=log(e^li/Z);
   *
   */
  private double[] normalize(double[] x) {
    double[] x1 = new double[x.length];
    copy(x1, x);
    //the priors
    double[] sums = new double[numClasses];
    for (int c = 0; c < numClasses; c++) {
      int priorc = indexOf(c);
      sums[c] += x[priorc];
    }
    double total = ArrayMath.logSum(sums);
    for (int c = 0; c < numClasses; c++) {
      int priorc = indexOf(c);
      x1[priorc] -= total;
    }
    //the features
    for (int c = 0; c < numClasses; c++) {
      for (int f = 0; f < numFeatures; f++) {
        double[] vals = new double[numValues[f]];
        for (int val = 0; val < numValues[f]; val++) {
          int index = indexOf(f, c, val);
          vals[val] = x[index];
        }
        total = ArrayMath.logSum(vals);
        for (int val = 0; val < numValues[f]; val++) {
          int index = indexOf(f, c, val);
          x1[index] -= total;
        }
      }
    }
    return x1;
  }

  @Override
  protected void calculate(double[] x1) {

    double[] x = normalize(x1);
    double[] xExp = new double[x.length];
    for (int i = 0; i < x.length; i++) {
      xExp[i] = Math.exp(x[i]);
    }
    value = 0.0;
    Arrays.fill(derivative, 0.0);
    double[] sums = new double[numClasses];
    double[] probs = new double[numClasses];
    // double[] counts = new double[numClasses];
    // Arrays.fill(counts, 0.0); // not needed; Java arrays zero initialized
    for (int d = 0; d < data.length; d++) {
      int[] features = data[d];
      // activation
      Arrays.fill(sums, 0.0);
      for (int c = 0; c < numClasses; c++) {
        int priorc = indexOf(c);
        sums[c] += x[priorc];
        for (int f = 0; f < features.length; f++) {
          int i = indexOf(f, c, features[f]);
          sums[c] += x[i];
        }
      }
      // expectation (slower routine replaced by fast way)
      // double total = Double.NEGATIVE_INFINITY;
      // for (int c=0; c<numClasses; c++) {
      //   total = SloppyMath.logAdd(total, sums[c]);
      // }
      double total = ArrayMath.logSum(sums);
      for (int c = 0; c < numClasses; c++) {
        probs[c] = Math.exp(sums[c] - total);
        int priorc = indexOf(c);
        derivative[priorc] += probs[c];
        for (int f = 0; f < features.length; f++) {
          for (int val = 0; val < numValues[f]; val++) {
            int i = indexOf(f, c, val);
            double thetha = xExp[i];
            derivative[i] -= probs[c] * thetha;
            if (labels[d] == c) {
              derivative[i] += thetha;
            }
          }
        }
      }
      // observed
      for (int f = 0; f < features.length; f++) {
        int i = indexOf(f, labels[d], features[f]);
        derivative[i] -= 1.0;
        for (int c = 0; c < numClasses; c++) {
          int i1 = indexOf(f, c, features[f]);
          derivative[i1] += probs[c];
        }
      }
      value -= sums[labels[d]] - total;
      int priorc = indexOf(labels[d]);
      derivative[priorc] -= 1;
    }
    // priors
    if (prior == QUADRATIC_PRIOR) {
      double sigmaSq = sigma * sigma;
      for (int i = 0; i < x1.length; i++) {
        double k = 1.0;
        double w = x1[i];
        value += k * w * w / 2.0 / sigmaSq;
        derivative[i] += k * w / sigmaSq;
      }
    } else if (prior == HUBER_PRIOR) {
      double sigmaSq = sigma * sigma;
      for (int i = 0; i < x1.length; i++) {
        double w = x1[i];
        double wabs = Math.abs(w);
        if (wabs < epsilon) {
          value += w * w / 2.0 / epsilon / sigmaSq;
          derivative[i] += w / epsilon / sigmaSq;
        } else {
          value += (wabs - epsilon / 2) / sigmaSq;
          derivative[i] += ((w < 0.0) ? -1.0 : 1.0) / sigmaSq;
        }
      }
    } else if (prior == QUARTIC_PRIOR) {
      double sigmaQu = sigma * sigma * sigma * sigma;
      for (int i = 0; i < x.length; i++) {
        double k = 1.0;
        double w = x1[i];
        value += k * w * w * w * w / 2.0 / sigmaQu;
        derivative[i] += k * w / sigmaQu;
      }
    }
    // else no prior

    /*
    System.out.println("N: "+data.length);
    System.out.println("Value: "+value);
    double ds = 0.0;
    for (int i=0; i<x.length; i++) {
      ds += derivative[i];
      System.out.println(i+" is: "+derivative[i]);
    }
    */
    //System.out.println("Deriv sum is: "+ds);
  }


  public LogConditionalEqConstraintFunction(int numFeatures, int numClasses, int[][] data, int[] labels) {
    this(numFeatures, numClasses, data, labels, 1.0);
  }

  public LogConditionalEqConstraintFunction(int numFeatures, int numClasses, int[][] data, int[] labels, double sigma) {
    this(numFeatures, numClasses, data, labels, QUADRATIC_PRIOR, sigma, 0.0);
  }


  public LogConditionalEqConstraintFunction(int numFeatures, int numClasses, int[][] data, int[] labels, int prior, double sigma, double epsilon) {
    this.numFeatures = numFeatures;
    this.numClasses = numClasses;
    this.data = data;
    this.labels = labels;
    if (prior >= 0 && prior <= QUARTIC_PRIOR) {
      this.prior = prior;
    } else {
      throw new IllegalArgumentException("Invalid prior: " + prior);
    }
    this.epsilon = epsilon;
    this.sigma = sigma;
    numValues = NaiveBayesClassifierFactory.numberValues(data, numFeatures);
    for (int i = 0; i < numValues.length; i++) {
      System.out.println("numValues " + i + " " + numValues[i]);
    }
    featureIndex = createIndex();
  }

  /**
   * use a random starting point uniform -1 1
   *
   */
  @Override
  public double[] initial() {
    double[] initial = new double[domainDimension()];
    for (int i = 0; i < initial.length; i++) {
      double r = Math.random();
      r -= .5;
      initial[i] = r;
    }
    return initial;
  }

}
