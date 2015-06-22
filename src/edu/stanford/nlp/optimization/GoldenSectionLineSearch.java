package edu.stanford.nlp.optimization;

import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.Generics;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Map;
import java.util.Arrays;

/**
 * A class to do golden section line search.  Should it implement Minimizer?  Prob. not.
 *
 * @author Galen Andrew
 */
public class GoldenSectionLineSearch implements LineSearcher {

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

  public double minimize(Function<Double, Double> function, double tol, double low, double high) {
    this.tol = tol;
    this.low = low;
    this.high = high;
    return minimize(function);
  }


  public double minimize(Function<Double, Double> function) {

    double tol = this.tol;
    double low = this.low;
    double high = this.high;

    // cdm Oct 2006: The code used to do nothing to find or check
    // the validity of an initial
    // bracketing; it just blindly placed the midpoint at the golden ratio
    // I now try to grid search a little in case the function is very flat
    // (RTE contradictions).

    double flow = function.apply(low);
    double fhigh = function.apply(high);
    if (VERBOSE) {
      System.err.println("Finding min between " + low + " (value: " +
                flow + ") and " + high + " (value: " + fhigh + ")");
    }

    double mid;
    double oldY;
    boolean searchRight;
    if (false) {
      // initialize with golden means
      mid = goldenMean(low, high);
      oldY = function.apply(mid);
      if (VERBOSE) System.err.println("Initially probed at " + mid + ", value is " + oldY);
      if (oldY < flow || oldY < fhigh) {
        searchRight = false; // Galen had this true; should be false
      } else {
        mid = goldenMean(high, low);
        oldY = function.apply(mid);
        if (VERBOSE) System.err.println("Probed at " + mid + ", value is " + oldY);
        searchRight = true;
        if ( ! (oldY < flow || oldY < fhigh)) {
          System.err.println("Warning: GoldenSectionLineSearch init didn't find slope!!");
        }
      }
    } else {
        // grid search a little; this case doesn't do geometric differently...
        if (VERBOSE) System.err.println("20 point gridsearch for good mid point....");
        double bestPoint = low;
        double bestVal = flow;
        double incr = (high - low)/22.0;
        for (mid = low+incr; mid < high; mid += incr) {
          oldY = function.apply(mid);
          if (VERBOSE) System.err.print("Probed at " + mid + ", value is " + oldY);
          if (oldY < bestVal) {
            bestPoint = mid;
            bestVal = oldY;
            if (VERBOSE) System.err.print(" [best so far!]");
          }
          if (VERBOSE) System.err.println();
        }
        mid = bestPoint;
        oldY = bestVal;
        searchRight = mid < low + (high - low)/2.0;
        if (oldY < flow && oldY < fhigh) {
          if (VERBOSE) System.err.println("Found a good mid point at (" + mid + ", " + oldY + ")");
        } else {
          System.err.println("Warning: GoldenSectionLineSearch grid search couldn't find slope!!");
          // revert to initial positioning and pray
          mid = goldenMean(low, high);
          oldY = function.apply(mid);
          searchRight = false;
        }
    }

    memory.put(mid, oldY);
    while (geometric ? (high / low > 1 + tol) : high - low > tol) {
      if (VERBOSE) System.err.println("Current low, mid, high: " + nf.format(low) + " " + nf.format(mid) + " " + nf.format(high));
      double newX = goldenMean(searchRight ? high : low, mid);
      double newY = function.apply(newX);
      memory.put(newX, newY);
      if (VERBOSE) System.err.println("Probed " + (searchRight ? "right": "left") + " at " + newX + ", value is " + newY);
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
   * dump the <x,y> pairs it computed found
   */
  public void dumpMemory() {
    Double[] keys = memory.keySet().toArray(new Double[memory.keySet().size()]);
    Arrays.sort(keys);
    for (Double key : keys) {
      System.err.println(key + "\t" + memory.get(key));
    }
  }


  public void discretizeCompute(Function<Double, Double> function, int numPoints, double low, double high) {
    double inc = (high - low) / numPoints;
    memory = Generics.newHashMap();
    for (int i = 0; i < numPoints; i++) {
      double x = low + i * inc;
      double y = function.apply(x);
      memory.put(x, y);
      System.err.println("for point " + x + "\t" + y);
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
    Function<Double, Double> f1 = new Function<Double, Double>() {
      public Double apply(Double x) {
        return Math.log(x * x - x + 1);
      }
    };
    System.out.println(min.minimize(f1));
    System.out.println();

    min = new GoldenSectionLineSearch(false, 0.00001, 0.0, 1.0);
    Function<Double,Double> f2 = new Function<Double,Double>() {
       public Double apply(Double x) {
         // this function used to fail in Galen's version; min should be 0.2
         // return - x * (2 * x - 1) * (x - 0.8);
         // this function fails if you don't find an initial bracketing
         return x < 0.1 ? 0.0: (x > 0.2 ? 0.0: (x - 0.1) * (x - 0.2));
         // return - Math.sin(x * Math.PI);
         // return -(3 + 6 * x - 4 * x * x);
       }
    };

    System.out.println(min.minimize(f2));
  } // end main

}
