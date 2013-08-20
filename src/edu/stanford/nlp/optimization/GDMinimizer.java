package edu.stanford.nlp.optimization;

/**
 * opimization using gradient descent
 * go in the direction of the negative derivative with a step size alpha
 */
public class GDMinimizer implements Minimizer<DiffFunction> {

  private Function monitor;
  private boolean verbose = false;
  private int ITMAX = 100;
  private int numToPrint = 100;
  private boolean silent = false; //should be false by default.
  private double stepSize = -.1; 
  private boolean adaptive = true;
  private static final double EPS = 1.0e-30;

  public GDMinimizer(Function monitor) {
    this.monitor = monitor;
  }

  public double[] minimize(DiffFunction function, double functionTolerance, double[] initial) {
			return minimize(function, functionTolerance, initial, ITMAX);
	}

  public void setAdaptiveStepSize(boolean flag){
    adaptive = flag;
  }
  
  public void setSilence(boolean flag){
    silent  = flag;
  }
  
  public void setStepSize(double stepSize){
    this.stepSize = -Math.abs(stepSize);
    //we always want stepsize to be negative because we are doing gradient descent.    
  }
  
  public void setVerbose(boolean flag){
    verbose = flag;
  }

  public double[] minimize(DiffFunction dfunction, double functionTolerance, double[] initial, int maxIterations) {

    int dimension = dfunction.domainDimension();
    //lastXx = 1.0;

    // evaluate function
    double fp = dfunction.valueAt(initial);
    if (verbose) {
      System.err.println("Initial: " + fp);
    }
    double[] xi = copyArray(dfunction.derivativeAt(initial));
    if (verbose) {
      System.err.println("Initial at: " + arrayToString(initial, numToPrint));
      System.err.println("Initial deriv: " + arrayToString(xi, numToPrint));
    }

    double[] p = new double[dimension];

    for (int j = 0; j < dimension; j++) {
      // g[j] = -1.0*xi[j];
      //xi[j] = g[j];
      // h[j] = g[j];
      p[j] = initial[j];
    }

    //double monitorReturn = monitor.valueAt(p);

    // iterations
    for (int iterations = 1; iterations < maxIterations; iterations++) {

      if (verbose) {
        System.err.println("Iter " + iterations + " ");
      }
      // do a line min along descent direction
      //System.err.println("Minimizing from ("+p[0]+","+p[1]+") along ("+xi[0]+","+xi[1]+")\n");
      if (verbose) {
        System.err.println("Minimizing along " + arrayToString(xi, numToPrint));
      }
      //System.err.println("Current is "+fp);
      double fp2 = fp + 1;

      double step = stepSize * 2;
      double[] p2 = null;
      int iter = 0;
      while (fp2 > fp) {
        step /= 2;
        if(Math.abs(step) < 1e-12){
          if(!silent)
            System.err.println("Non convex surface: stuck at local minimum. Returning.");
          return p;
        }
        iter++;
        p2 = addVector(p, xi, step);
        fp2 = dfunction.valueAt(p2);
        if(verbose) //change this to verbose.
          System.err.println("doing step " + step + " fp " + fp + " fp2 " + fp2);
      }

      if ((iter == 1) && adaptive) {
        stepSize *= 1.1;
      }
      if ((iter > 1) && adaptive) {
        stepSize = step;
      }
      //shift p

      //System.err.println("Result is "+fp2+" (from "+fp+") at ("+p2[0]+","+p2[1]+")\n");
      if (verbose) {
        System.err.println("Result is " + fp2 + " after " + iterations);
        System.err.println("Result at " + arrayToString(p2, numToPrint));
      }
      //System.err.print(fp2+"|"+(int)(Math.log((fabs(fp2-fp)+1e-100)/(fabs(fp)+fabs(fp2)+1e-100))/Math.log(10)));
      if (verbose) {
        System.err.println(" " + fp2);
      }
      if (monitor != null) {
        double monitorReturn = monitor.valueAt(p2);
        if (monitorReturn < functionTolerance) {
          if(!silent)
            System.err.println("converged iterations " + iterations + " forced by monitor");
          return p2;
        }
      }
      // check convergence
      if (2.0 * fabs(fp2 - fp) <= functionTolerance * (fabs(fp2) + fabs(fp) + EPS)) {
        // convergence
        //if (!checkSimpleGDConvergence || simpleGDStep || simpleGD) {
        if(!silent)
          System.err.println("convereged iterations " + iterations);
        return p2;

      } else {
      }

      for (int j = 0; j < dimension; j++) {
        p[j] = p2[j];
      }
      fp = fp2;
      // find the new gradient
      xi = copyArray(dfunction.derivativeAt(p));

    }

    // too many iterations
    if(!silent)
      System.err.println("Warning: exiting minimize because ITER exceeded!");
    return p;

  }

  double[] copyArray(double[] a) {
    double[] result = new double[a.length];
    for (int i = 0; i < a.length; i++) {
      result[i] = a[i];
    }
    return result;
  }

  /*
  private static String arrayToString(double[] x) {
    return arrayToString(x, x.length);
  }
  */

  private static String arrayToString(double[] x, int num) {
    StringBuffer sb = new StringBuffer("(");
    if (num > x.length) {
      num = x.length;
    }
    for (int j = 0; j < num; j++) {
      sb.append(x[j]);
      if (j != x.length - 1) {
        sb.append(", ");
      }
    }
    if (num < x.length) {
      sb.append("...");
    }
    sb.append(")");
    return sb.toString();
  }

  private double fabs(double x) {
    if (x < 0) {
      return -1.0 * x;
    }
    return x;
  }

  /*
  private double fmax(double x, double y) {
    if (x < y) {
      return y;
    }
    return x;
  }

  private double fmin(double x, double y) {
    if (x > y) {
      return y;
    }
    return x;
  }

  private double sign(double x, double y) {
    if (y >= 0) {
      return fabs(x);
    }
    return -1.0 * fabs(x);
  }

  
  private double arrayMax(double[] x) {
    double max = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < x.length; i++) {
      if (max < x[i]) {
        max = x[i];
      }
    }
    return max;
  }

  private int arrayArgMax(double[] x) {
    double max = Double.NEGATIVE_INFINITY;
    int index = -1;
    for (int i = 0; i < x.length; i++) {
      if (max < x[i]) {
        max = x[i];
        index = i;
      }
    }
    return index;
  }

  private double arrayMin(double[] x) {
    double min = Double.POSITIVE_INFINITY;
    for (int i = 0; i < x.length; i++) {
      if (min > x[i]) {
        min = x[i];
      }
    }
    return min;
  }
*/

  private double[] addVector(double[] p, double[] derivative, double step) {
    double[] ret = new double[p.length];
    for (int j = 0; j < p.length; j++) {
      ret[j] = p[j] + derivative[j] * step;
    }
    return ret;
  }


}
