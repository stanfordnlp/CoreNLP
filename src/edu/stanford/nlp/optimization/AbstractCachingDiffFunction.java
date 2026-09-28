package edu.stanford.nlp.optimization;

import edu.stanford.nlp.util.logging.Redwood;

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
public abstract class AbstractCachingDiffFunction implements DiffFunction, HasInitial  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(AbstractCachingDiffFunction.class);

  private double[] lastX; // = null;
  private int fEvaluations; // = 0;
  /** The derivative at the last point evaluated; set by {@link #calculate(double[])}. */
  protected double[] derivative; // = null;
  /** The value at the last point evaluated; set by {@link #calculate(double[])}. */
  protected double value; // = 0.0;
  private final Random generator = new Random(2147483647L);

  /** Creates a function with an empty cache. */
  public AbstractCachingDiffFunction() { }

  /**
   * Check the derivative against a finite difference approximation at
   * the {@link #initial()} point, using 100 regular and 50 random checks.
   *
   * @return true if every checked component of the derivative is close
   *         to the approximation
   * @see #gradientCheck(int, int, double[])
   */
  public boolean gradientCheck() {
    return gradientCheck(100, 50, initial());
  }

  /**
   * Check the derivative against a central finite difference
   * approximation, one component at a time. The components checked are
   * about {@code numOfChecks} evenly spaced indices, the first and last
   * {@code numOfChecks} indices, and {@code numOfRandomChecks} random
   * indices. A component fails if it differs from the approximation by
   * more than 0.01 absolute and 10% relative. The result of each check
   * is printed to stderr.
   *
   * @param numOfChecks Controls the number of regularly spaced and
   *                    boundary indices checked
   * @param numOfRandomChecks Number of randomly chosen indices to check
   * @param x The point at which to check; it is modified during the
   *          check and restored afterwards
   * @return true if every checked component passes
   */
  public boolean gradientCheck(int numOfChecks, int numOfRandomChecks, double[] x) {
    double epsilon = 1e-5;
    double diffThreshold = 0.01;
    double diffPctThreshold = 0.1;
    double twoEpsilon = epsilon * 2;
    int xLen = x.length;
    // log.info("\n\n\ncalling derivativeAt");
    derivativeAt(x);
    double[] savedDeriv = new double[xLen];
    System.arraycopy(derivative, 0, savedDeriv, 0, derivative.length);
    int interval = Math.max(1, x.length / numOfChecks);
    Set<Integer> indicesToCheck = new TreeSet<>();
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
    List<Integer> badIndices = new ArrayList<>();
    for (int paramIndex: indicesToCheck) {
      double oldX = x[paramIndex];
      x[paramIndex] = oldX + epsilon;
      // log.info("\n\n\ncalling valueAt1");
      double plusVal = valueAt(x);
      x[paramIndex] = oldX - epsilon;
      // log.info("\n\n\ncalling valueAt2");
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
      log.info("Bad indices: ");
      for (int i = 0; i < badIndices.size() && i < 10; ++i) {
        log.info(" " + badIndices.get(i));
      }
      if (badIndices.size() >= 10) {
        log.info(" (...)");
      }
      log.info();
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
    return new double[domainDimension()]; // initialized with 0s.
  }

  /**
   * Returns a starting point with each component drawn uniformly from [0, 1).
   *
   * @return A new random point of size {@link #domainDimension()}
   */
  public double[] randomInitial() {
    double[] initial = new double[domainDimension()];
    for (int i = 0; i < initial.length; i++) {
      initial[i] = generator.nextDouble();
    }
    return initial;
  }

  /**
   * Copy the contents of one array into another.
   *
   * @param copy The destination; must be at least as long as {@code orig}
   * @param orig The source
   */
  protected static void copy(double[] copy, double[] orig) {
    System.arraycopy(orig, 0, copy, 0, orig.length);
  }

  /**
   * Make sure {@link #value} and {@link #derivative} hold the results
   * for {@code x}, calling {@link #calculate(double[])} unless {@code x}
   * equals the last point evaluated.
   *
   * @param x The point at which to evaluate the function
   */
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

  /**
   * Returns the value at the last point evaluated, without recalculating.
   *
   * @return The cached value
   */
  public double lastValue() {
    return value;
  }

  /**
   * Returns the derivative at the last point evaluated, without
   * recalculating. This is the cached array itself, not a copy.
   *
   * @return The cached derivative
   */
  public double[] getDerivative() {
    return derivative;
  }

}
