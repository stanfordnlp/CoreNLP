package edu.stanford.nlp.optimization;

/**
 * Class CoordinatewiseGoldenSectionMinimizer
 * Minimizes a function implementing Function using no gradient information, one coordinate at a time,
 * until it has reached a point that is within functionTolerance of the true minimum.
 * Uses Golden section search.
 *
 * @author Teg Grenager
 */
public class CoordinatewiseGoldenSectionMinimizer implements Minimizer {

  private static double GOLDEN_RATIO = (1.0 + Math.sqrt(5.0)) / 2.0;
  private static double GOLDEN_SECTION = (GOLDEN_RATIO / (1.0 + GOLDEN_RATIO));
  private static int numStepsPerCoordinate = 0;

  public double[] minimize(Function function, double functionTolerance, double[] initial) {
			return minimize(function, functionTolerance, initial, -1);
	}

	public double[] minimize(Function function, double functionTolerance, double[] initial, int maxIterations) {
    double[] current = new double[initial.length];
    System.arraycopy(initial, 0, current, 0, initial.length);
    boolean changed = true;
		int iteration = 0;
		boolean have_max = (maxIterations > 0);
    while (changed && (iteration++ <= maxIterations || !have_max)) {
      changed = false;
      // go through coordinates minimizing each in turn
      for (int i = 0; i < function.domainDimension(); i++) {
        System.out.print(i + ": ");
        double oldX = current[i];
        lineSearch(function, current, i, functionTolerance); // changes current[i]
        if (oldX != current[i]) {
          changed = true;
          System.out.println("changed from " + oldX + " to " + current[i]);
        } else {
          System.out.println("didn't change");
        }
      }
      System.out.print("current point: ");
      for (int i = 0; i < current.length; i++) {
        System.out.print(current[i] + "\t");
      }
      System.out.println();
    }
    return current;
  }

  private double lineSearch(Function function, double[] current, int i, double functionTolerance) {
    double initialX = current[i];
    double initialY = function.valueAt(current);

    // find high bracket
    System.out.println("finding high bracket");
    double deltaX0 = functionTolerance;
    double closeX0 = initialX;
    double farX0 = initialX + deltaX0;
    double closeY0 = initialY;
    current[i] = farX0;
    double farY0 = function.valueAt(current);
    while (farY0 <= closeY0 && farX0 < initialX + functionTolerance * 1e12) {
      System.out.println("farX0=" + farX0 + " farY0=" + farY0);
      closeX0 = farX0;
      closeY0 = farY0;
      deltaX0 *= 2.0;
      farX0 = current[i] + deltaX0;
      current[i] = farX0;
      farY0 = function.valueAt(current);
    }
    System.out.println("farX0=" + farX0 + " farY0=" + farY0);
    if (farX0 >= initialX + functionTolerance * 1e12) {
      // we exceeded max size in this direction
      return closeX0;
    }
    if (closeX0 != initialX && closeY0 != initialY) {
      // we found a local minimum above initial, we use that
      if (!isValid(initialY, closeY0, farY0)) {
        throw new RuntimeException();
      }
      return goldenSectionSearch(initialX, initialY, closeX0, closeY0, farX0, farY0, current, i, function, functionTolerance, false, 0);  // changes current[i]
    } else {

      // no local minimum above initial
      // find low bracket
      System.out.println("finding low bracket");
      double deltaX1 = functionTolerance;
      double closeX1 = initialX;
      double farX1 = initialX - deltaX1;
      double closeY1 = initialY;
      current[i] = farX1;
      double farY1 = function.valueAt(current);
      while (farY1 <= closeY1 && farX1 > initialX - functionTolerance * 1e12) {
        System.out.println("farX1=" + farX1 + " farY1=" + farY1);
        closeX1 = farX1;
        closeY1 = farY1;
        deltaX1 *= 2.0;
        farX1 = current[i] - deltaX1;
        current[i] = farX1;
        farY1 = function.valueAt(current);
      }
      System.out.println("farX1=" + farX1 + " farY1=" + farY1);
      if (farX1 <= initialX - functionTolerance * 1e12) {
        // we exceeded max size in this direction
        return closeX1;
      }
      if (closeX1 != initialX && closeY1 != initialY) {
        // we found a local minimum above initial, we use that
        if (!isValid(farY1, closeY1, initialY)) {
          throw new RuntimeException();
        }
        return goldenSectionSearch(farX1, farY1, closeX1, closeY1, initialX, initialY, current, i, function, functionTolerance, true, 0);  // changes current[i]
      } else {
        // we couldn't find a minimum lower either
        current[i] = initialX;
        return initialY;
      }
    }
  }

  private boolean isValid(double lowY, double midY, double highY) {
    //    System.out.println("lowY=" + lowY + " midY=" + midY+ " highY=" + highY);
    return (midY < Math.min(lowY, highY));
  }

  private double goldenSectionSearch(double bottomX, double bottomY, double midX, double midY, double topX, double topY, double[] current, int i, Function function, double functionTolerance, boolean divideLeft, int level) {
    System.out.println("goldenSectionSearch(" + bottomX + ", " + midX + ", " + topX + ",      " + bottomY + ", " + midY + ", " + topY + ", " + level + ")");
    if (divideLeft) {
      // if space is small then we're done
      if (level >= numStepsPerCoordinate || midX - bottomX <= functionTolerance) {
        current[i] = midX;
        return midY;
      }
      // pick new point on the right side of left
      double newX = bottomX + (midX - bottomX) * GOLDEN_SECTION;
      current[i] = newX;
      double newY = function.valueAt(current);
      if (newY < midY) {
        return goldenSectionSearch(bottomX, bottomY, newX, newY, midX, midY, current, i, function, functionTolerance, true, level + 1);
      } else {
        return goldenSectionSearch(newX, newY, midX, midY, topX, topY, current, i, function, functionTolerance, false, level + 1);
      }
    } else {
      // if space is small then we're done
      if (level >= numStepsPerCoordinate || topX - midX <= functionTolerance) {
        current[i] = midX;
        return midY;
      }
      // pick new point on the left side of right
      double newX = midX + (topX - midX) * (1.0 - GOLDEN_SECTION);
      current[i] = newX;
      double newY = function.valueAt(current);
      if (newY < midY) {
        return goldenSectionSearch(midX, midY, newX, newY, topX, topY, current, i, function, functionTolerance, false, level + 1);
      } else {
        return goldenSectionSearch(bottomX, bottomY, midX, midY, newX, newY, current, i, function, functionTolerance, true, level + 1);
      }
    }
  }

  private static double[] difference(double[] a, double[] b) {
    double[] result = new double[a.length];
    for (int i = 0; i < a.length; i++) {
      result[i] = a[i] - b[i];
    }
    return result;
  }

  private static double sumOfSquares(double[] a) {
    double result = 0.0;
    for (int i = 0; i < a.length; i++) {
      result += (a[i] * a[i]);
    }
    return result;
  }

  public static void main(String[] args) {
    // all the args are double min values
    double[] min = new double[args.length];
    for (int i = 0; i < args.length; i++) {
      min[i] = Double.parseDouble(args[i]);
    }
    final double[] offset = min;
    // now create a multi-dimensional quadratic function with that min
    Function f = new Function() {
      public double valueAt(double[] x) {
        return sumOfSquares(difference(x, offset));
      }

      public int domainDimension() {
        return offset.length;  //To change body of implemented methods use File | Settings | File Templates.
      }
    };
    Minimizer m = new CoordinatewiseGoldenSectionMinimizer();
    double[] answer = m.minimize(f, .01, new double[offset.length]); // start at all zeros
    System.out.print("minimum: ");
    for (int i = 0; i < answer.length; i++) {
      System.out.print(answer[i] + "\t");
    }
    System.out.println();
  }
}
