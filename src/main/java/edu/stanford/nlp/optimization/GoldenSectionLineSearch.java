package edu.stanford.nlp.optimization;

import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.logging.Redwood;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Map;
import java.util.Arrays;
import java.util.function.DoubleUnaryOperator;

/**
 * A class to do golden section line search.  Should it implement Minimizer?  Prob. not.
 *
 * @author Galen Andrew
 */
public class GoldenSectionLineSearch implements LineSearcher  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(GoldenSectionLineSearch.class);

  private static final double GOLDEN_RATIO = (1.0 + Math.sqrt(5.0)) / 2.0;
  private static final double GOLDEN_SECTION = (GOLDEN_RATIO / (1.0 + GOLDEN_RATIO));
  private static boolean VERBOSE = true;

  private Map<Double, Double> memory = Generics.newHashMap(); //remember where it was called and what were the values
  private boolean geometric;

  private double tol;
  private double low;
  private double high;

  public GoldenSectionLineSearch(double tol, double low, double high) {
    this(false, tol, low, high);
  }

  public GoldenSectionLineSearch(double tol, double low, double high, boolean verbose) {
    this(false, tol, low, high, verbose);
  }

  public GoldenSectionLineSearch(boolean geometric) {
    this(geometric, 1e-4, 1e-2, 10);
  }

  public GoldenSectionLineSearch(boolean geometric, double tol, double low, double high) {
    this.geometric = geometric;
    this.tol = tol;
    this.low = low;
    this.high = high;
  }

  public GoldenSectionLineSearch(boolean geometric, double tol, double low, double high, boolean verbose) {
    this.geometric = geometric;
    this.tol = tol;
    this.low = low;
    this.high = high;
    GoldenSectionLineSearch.VERBOSE = verbose;
  }

  private static final NumberFormat nf = new DecimalFormat("0.000");

  public double minimize(DoubleUnaryOperator function, double tol, double low, double high) {
    this.tol = tol;
    this.low = low;
    this.high = high;
    return minimize(function);
  }


  public double minimize(DoubleUnaryOperator function) {

    double tol = this.tol;
    double low = this.low;
    double high = this.high;

    // cdm Oct 2006: The code used to do nothing to find or check
    // the validity of an initial
    // bracketing; it just blindly placed the midpoint at the golden ratio
    // I now try to grid search a little in case the function is very flat
    // (RTE contradictions).

    double flow = function.applyAsDouble(low);
    double fhigh = function.applyAsDouble(high);
    if (VERBOSE) {
      log.info("Finding min between " + low + " (value: " +
                flow + ") and " + high + " (value: " + fhigh + ')');
    }

    double mid;
    double oldY;
    boolean searchRight;
    if (false) {
      // initialize with golden means
      mid = goldenMean(low, high);
      oldY = function.applyAsDouble(mid);
      if (VERBOSE) log.info("Initially probed at " + mid + ", value is " + oldY);
      if (oldY < flow || oldY < fhigh) {
        searchRight = false; // Galen had this true; should be false
      } else {
        mid = goldenMean(high, low);
        oldY = function.applyAsDouble(mid);
        if (VERBOSE) log.info("Probed at " + mid + ", value is " + oldY);
        searchRight = true;
        if ( ! (oldY < flow || oldY < fhigh)) {
          log.info("Warning: GoldenSectionLineSearch init didn't find slope!!");
        }
      }
    } else {
        // grid search a little; this case doesn't do geometric differently...
        if (VERBOSE) log.info("20 point gridsearch for good mid point....");
        double bestPoint = low;
        double bestVal = flow;
        double incr = (high - low)/22.0;
        for (mid = low+incr; mid < high; mid += incr) {
          oldY = function.applyAsDouble(mid);
          if (VERBOSE) log.info("Probed at " + mid + ", value is " + oldY);
          if (oldY < bestVal) {
            bestPoint = mid;
            bestVal = oldY;
            if (VERBOSE) log.info(" [best so far!]");
          }
          if (VERBOSE) log.info();
        }
        mid = bestPoint;
        oldY = bestVal;
        searchRight = mid < low + (high - low)/2.0;
        if (oldY < flow && oldY < fhigh) {
          if (VERBOSE) log.info("Found a good mid point at (" + mid + ", " + oldY + ')');
        } else {
          log.info("Warning: GoldenSectionLineSearch grid search couldn't find slope!!");
          // revert to initial positioning and pray
          mid = goldenMean(low, high);
          oldY = function.applyAsDouble(mid);
          searchRight = false;
        }
    }

    memory.put(mid, oldY);
    while (geometric ? (high / low > 1 + tol) : high - low > tol) {
      if (VERBOSE) log.info("Current low, mid, high: " + nf.format(low) + ' ' + nf.format(mid) + ' ' + nf.format(high));
      double newX = goldenMean(searchRight ? high : low, mid);
      double newY = function.applyAsDouble(newX);
      memory.put(newX, newY);
      if (VERBOSE) log.info("Probed " + (searchRight ? "right": "left") + " at " + newX + ", value is " + newY);
      if (newY < oldY) {
        // keep going in this direction
        if (searchRight) low = mid; else high = mid;
        mid = newX;
        oldY = newY;
      } else {
        // go the other way
        if (searchRight) high = newX; else low = newX;
        searchRight = !searchRight;
      }
    }

    return mid;
  }

  /**
   * dump the {@code <x,y>} pairs it computed found
   */
  public void dumpMemory() {
    Double[] keys = memory.keySet().toArray(new Double[memory.keySet().size()]);
    Arrays.sort(keys);
    for (Double key : keys) {
      log.info(key + "\t" + memory.get(key));
    }
  }


  public void discretizeCompute(DoubleUnaryOperator function, int numPoints, double low, double high) {
    double inc = (high - low) / numPoints;
    memory = Generics.newHashMap();
    for (int i = 0; i < numPoints; i++) {
      double x = low + i * inc;
      double y = function.applyAsDouble(x);
      memory.put(x, y);
      log.info("for point " + x + '\t' + y);
    }
    dumpMemory();
  }

  /**
   * The point that is the GOLDEN_SECTION along the way from a to b.
   * a may be less or greater than b, you find the point 60-odd percent
   * of the way from a to b.
   *
   * @param a Interval minimum
   * @param b Interval maximum
   * @return The GOLDEN_SECTION along the way from a to b.
   */
  private double goldenMean(double a, double b) {
    if (geometric) {
      return a * Math.pow(b / a, GOLDEN_SECTION);
    } else {
      return a + (b - a) * GOLDEN_SECTION;
    }
  }

  public static void main(String[] args) {
    GoldenSectionLineSearch min =
        new GoldenSectionLineSearch(true, 0.00001, 0.001, 121.0);
    DoubleUnaryOperator f1 = x -> Math.log(x * x - x + 1);
    System.out.println(min.minimize(f1));
    System.out.println();

    min = new GoldenSectionLineSearch(false, 0.00001, 0.0, 1.0);
    DoubleUnaryOperator f2 = x -> x < 0.1 ? 0.0: (x > 0.2 ? 0.0: (x - 0.1) * (x - 0.2));

    System.out.println(min.minimize(f2));
  } // end main

}
