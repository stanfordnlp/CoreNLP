package edu.stanford.nlp.optimization;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.Random;
import java.util.Set;

/** A differentiable function that caches the last evaluation of its value and
 *  derivative.
 *
 *  @author Dan Klein
 */

public abstract class AbstractCachingDiffFunction implements DiffFunction, HasInitial {

  double[] lastX; // = null;
  int fEvaluations; // = 0;
  protected double[] derivative; // = null;
  protected double value; // = 0.0;
  protected Random generator = new Random(2147483647L);

  public boolean gradientCheck() {
    return gradientCheck(100, 50, initial());
  }

  public boolean gradientCheck(int numOfChecks, int numOfRandomChecks, double[] x) {
    double epsilon = 1e-5;
    double diffThreshold = 0.01;
    double diffPctThreshold = 0.1;
    double twoEpsilon = epsilon * 2;
    int xLen = x.length;
    // System.err.println("\n\n\ncalling derivativeAt");
    derivativeAt(x);
    double[] savedDeriv = new double[xLen];
    System.arraycopy(derivative, 0, savedDeriv, 0, derivative.length);
    int interval = Math.max(1, x.length / numOfChecks);
    Set<Integer> indicesToCheck = new TreeSet<Integer>();
    for (int paramIndex = 0; paramIndex < xLen; paramIndex+=interval) {
      indicesToCheck.add(paramIndex);
    }
    for (int i = xLen-1; i >= 0 && i > xLen-numOfChecks; i--) {
      indicesToCheck.add(i);
    }
    for (int i = 1; i < xLen && i < numOfChecks; i++) {
      indicesToCheck.add(i);
    }
    for (int i = 0; i < numOfRandomChecks; i++) {
      indicesToCheck.add(generator.nextInt(xLen));
    }
    boolean returnVal = true;
    List<Integer> badIndices = new ArrayList<Integer>();
    for (int paramIndex: indicesToCheck) {
      double oldX = x[paramIndex];
      x[paramIndex] = oldX + epsilon;
      // System.err.println("\n\n\ncalling valueAt1");
      double plusVal = valueAt(x);
      x[paramIndex] = oldX - epsilon;
      // System.err.println("\n\n\ncalling valueAt2");
      double minusVal = valueAt(x);
      double appDeriv = (plusVal - minusVal) / twoEpsilon;
      double calcDeriv = savedDeriv[paramIndex];
      double diff = Math.abs(appDeriv - calcDeriv);
      double pct = diff / Math.min(Math.abs(appDeriv), Math.abs(calcDeriv));
      if (diff > diffThreshold && pct > diffPctThreshold) {
        System.err.printf("Grad fail at %2d, appGrad=%9.7f, calcGrad=%9.7f, diff=%9.7f, pct=%9.7f\n", paramIndex,appDeriv,calcDeriv,diff,pct);
        badIndices.add(paramIndex);
        returnVal= false;
      } else {
        System.err.printf("Grad good at %2d, appGrad=%9.7f, calcGrad=%9.7f, diff=%9.7f, pct=%9.7f\n", paramIndex,appDeriv,calcDeriv,diff,pct);
      }
      x[paramIndex] = oldX;
    }
    if (returnVal){
      System.err.printf("ALL gradients passed. Yay!\n");
    } else {
      System.err.print("Bad indices: ");
      for (int i = 0; i < badIndices.size() && i < 10; ++i) {
        System.err.print(" " + badIndices.get(i));
      }
      if (badIndices.size() >= 10) {
        System.err.print(" (...)");
      }
      System.err.println();
    }
    return returnVal;
  }

  /**
   * Calculate the value at x and the derivative
   * and save them in the respective fields.
   *
   * @param x The point at which to calculate the function
   */
  protected abstract void calculate(double[] x);

  /**
   * Clears the cache in a way that doesn't require reallocation :-)
   */
  protected void clearCache() {
    if (lastX != null) lastX[0] = Double.NaN;
  }

  @Override
  public double[] initial() {
    double[] initial = new double[domainDimension()];
    // Arrays.fill(initial, 0.0); // You get zero fill of array for free in Java! (Like it or not....)
    return initial;
  }

  public double[] randomInitial() {
    double[] initial = new double[domainDimension()];
    for (int i = 0; i < initial.length; i++) {
      initial[i] = generator.nextDouble();
    }
    return initial;
  }

  protected static void copy(double[] copy, double[] orig) {
    System.arraycopy(orig, 0, copy, 0, orig.length);
  }

  public void ensure(double[] x) {
    if (Arrays.equals(x, lastX)) {
      return;
    }
    if (lastX == null) {
      lastX = new double[domainDimension()];
    }
    if (derivative == null) {
      derivative = new double[domainDimension()];
    }
    copy(lastX, x);
    fEvaluations += 1;
    calculate(x);
  }

  @Override
  public double valueAt(double[] x) {
    ensure(x);
    return value;
  }

  @Override
  public double[] derivativeAt(double[] x) {
    ensure(x);
    return derivative;
  }

  public double lastValue() {
    return value;
  }

  public double[] getDerivative() {
    return derivative;
  }

}
