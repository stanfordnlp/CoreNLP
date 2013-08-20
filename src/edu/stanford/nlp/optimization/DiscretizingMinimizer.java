package edu.stanford.nlp.optimization;



/**
 * Class DiscretizingMinimizer
 *
 * @author Teg Grenager
 */
public class DiscretizingMinimizer implements Minimizer {
  private int pointsPerDimension;
  private double functionTolerance;
  private double[] initialPoint;
  private double[] bestPoint;
  private double bestValue;

		/**
			 ignores maxIterations for now.
		**/
  public double[] minimize(Function function, double functionTolerance, double[] initial, int maxIterations) {
      return minimize(function, functionTolerance, initial);
  }

  public double[] minimize(Function function, double functionTolerance, double[] initial) {
    int pointsOnLeft = pointsPerDimension / 2; // int division rounds down
    // set initial to be the left corner
    initialPoint = new double[initial.length];
    for (int i = 0; i < initial.length; i++) {
      initialPoint[i] = initial[i] - (functionTolerance * pointsOnLeft);
    }
    bestPoint = new double[initial.length];
    this.functionTolerance = functionTolerance;
    bestValue = Double.POSITIVE_INFINITY;
    double[] partial = new double[initial.length];
    helper(function, 0, partial);
    return bestPoint;
  }

  private void helper(Function function, int d, double[] partial) {
    if (d == partial.length) {
      // we've got a complete vector, let's compute a value
      double value = function.valueAt(partial);
      if (value < bestValue) {
        System.out.println("value " + value + " is less than " + bestValue);
        System.arraycopy(partial, 0, bestPoint, 0, partial.length); // copy value into bestPoint to save it
        bestValue = value;
      }
    } else {
      // not yet complete, we iterate through our values and recurse
      for (int i = 0; i < pointsPerDimension; i++) {
        partial[d] = initialPoint[d] + (functionTolerance * i);
        helper(function, d + 1, partial);
      }
    }
  }

  public DiscretizingMinimizer(int pointsPerDimension) {
    this.pointsPerDimension = pointsPerDimension;
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
        System.out.print("point: ");
        for (int i = 0; i < x.length; i++) {
          System.out.print(x[i] + "\t");
        }
        double value = sumOfSquares(difference(x, offset));
        System.out.println(" value: " + value);
        return value;
      }

      public int domainDimension() {
        return offset.length;  //To change body of implemented methods use File | Settings | File Templates.
      }
    };
    Minimizer m = new DiscretizingMinimizer(11);
    double[] answer = m.minimize(f, 10.0, new double[offset.length]); // start at all zeros
    System.out.print("minimum: ");
    for (int i = 0; i < answer.length; i++) {
      System.out.print(answer[i] + "\t");
    }
    System.out.println();
  }

}
