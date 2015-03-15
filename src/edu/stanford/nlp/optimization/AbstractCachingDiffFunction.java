package edu.stanford.nlp.optimization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

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
  Random generator = new Random(2147483647L);

  public boolean gradientCheck() {
    return gradientCheck(100, 50, initial());
  }

  public boolean gradientCheck(int numOfChecks, int numOfRandomChecks, double[] x) {
    double epsilon = 1e-4;
    double diffThreshold = 5e-2;
    double diffPctThreshold = 1e-1;
    double twoEpsilon = epsilon * 2;
    int xLen = x.length;
    derivativeAt(x);
    double[] savedDeriv = new double[xLen];
    System.arraycopy(derivative, 0, savedDeriv, 0, derivative.length); 
    double oldX, plusVal, minusVal, appDeriv, calcDeriv, diff, pct = 0;
    int interval = x.length / numOfChecks;
    List<Integer> indicesToCheck = new ArrayList<Integer>();
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
    for (int paramIndex: indicesToCheck) {
      oldX = x[paramIndex];
      x[paramIndex] = oldX + epsilon;
      plusVal = valueAt(x);
      x[paramIndex] = oldX - epsilon;
      minusVal = valueAt(x);
      appDeriv = (plusVal - minusVal) / twoEpsilon;
      calcDeriv = savedDeriv[paramIndex];
      diff = Math.abs(appDeriv - calcDeriv);
      pct = diff / Math.min(Math.abs(appDeriv), Math.abs(calcDeriv));
      if (diff > diffThreshold && pct > diffPctThreshold) {
        System.err.println("Gradient check failed at index "+paramIndex+", appGrad=" + appDeriv+ ", calcGrad="+ calcDeriv + ", diff="+diff + ", pct=" + pct); 
        return false;
      } else {
        System.err.println("Gradient check passed at index "+paramIndex+", appGrad=" + appDeriv+ ", calcGrad="+ calcDeriv + ", diff="+diff + ", pct=" + pct); 
      }
      x[paramIndex] = oldX;
    }
    return true;
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

  void ensure(double[] x) {
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

}
