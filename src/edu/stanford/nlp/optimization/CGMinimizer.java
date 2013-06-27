package edu.stanford.nlp.optimization;

import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * Conjugate-gradient implementation based on the code in Numerical
 * Recipes in C.  (See p. 423 and others.)  As of now, it requires a
 * differentiable function (DiffFunction) as input.  Equality
 * constraints are supported; inequality constraints may soon be
 * added.
 * <p/>
 * The basic way to use the minimizer is with a null constructor, then
 * the simple minimize method:
 * <p/>
 * <p><code>Minimizer cgm = new CGMinimizer();</code>
 * <br><code>DiffFunction df = new SomeDiffFunction();</code>
 * <br><code>double tol = 1e-4;</code>
 * <br><code>double[] initial = getInitialGuess();</code>
 * <br><code>double[] minimum = cgm.minimize(df,tol,initial);</code>
 *
 * @author <a href="mailto:klein@cs.stanford.edu">Dan Klein</a>
 * @version 1.0
 * @since 1.0
 */
public class CGMinimizer implements Minimizer<DiffFunction> {

  private static NumberFormat nf = new DecimalFormat("0.000E0");

  private Function monitor; // = null;

  private static final int numToPrint = 5;
  private static final boolean simpleGD = false;
  private static final boolean checkSimpleGDConvergence = true;
  private static final boolean verbose = false;
  private boolean silent;

  private static final int ITMAX = 2000; // overridden in dbrent(); made bigger
  private static final double EPS = 1.0e-30;

  private static final int resetFrequency = 10;

  static double[] copyArray(double[] a) {
    double[] result = new double[a.length];
    for (int i = 0; i < a.length; i++) {
      result[i] = a[i];
    }
    return result;
  }

  //  private static String arrayToString(double[] x) {
  //    return arrayToString(x, x.length);
  //  }

  private static String arrayToString(double[] x, int num) {
    StringBuilder sb = new StringBuilder("(");
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

  private static double fabs(double x) {
    if (x < 0) {
      return -x;
    }
    return x;
  }

  private static double fmax(double x, double y) {
    if (x < y) {
      return y;
    }
    return x;
  }

  //  private static double fmin(double x, double y) {
  //    if (x>y)
  //      return y;
  //    return x;
  //  }

  private static double sign(double x, double y) {
    if (y >= 0.0) {
      return fabs(x);
    }
    return -fabs(x);
  }

  //  private static double arrayMax(double[] x) {
  //    double max = Double.NEGATIVE_INFINITY;
  //    for (int i=0; i<x.length; i++) {
  //      if (max < x[i])
  //	max = x[i];
  //    }
  //    return max;
  //  }
  //
  //  private static int arrayArgMax(double[] x) {
  //    double max = Double.NEGATIVE_INFINITY;
  //    int index = -1;
  //    for (int i=0; i<x.length; i++) {
  //      if (max < x[i]) {
  //	max = x[i];
  //	index = i;
  //      }
  //    }
  //    return index;
  //  }
  //
  //  private static double arrayMin(double[] x) {
  //    double min = Double.POSITIVE_INFINITY;
  //    for (int i=0; i<x.length; i++) {
  //      if (min > x[i])
  //	min = x[i];
  //    }
  //    return min;
  //  }
  //
  //  private static int arrayArgMin(double[] x) {
  //    double min = Double.POSITIVE_INFINITY;
  //    int index = -1;
  //    for (int i=0; i<x.length; i++) {
  //      if (min > x[i]) {
  //	min = x[i];
  //	index = i;
  //      }
  //    }
  //    return index;
  //  }

  static class OneDimDiffFunction {
    private DiffFunction function;
    private double[] initial;
    private double[] direction;
    private double[] tempVector;

    private double[] vectorOf(double x) {
      for (int j = 0; j < initial.length; j++) {
        tempVector[j] = initial[j] + x * direction[j];
      }
      //System.err.println("Tmp "+arrayToString(tempVector,10));
      //System.err.println("Dir "+arrayToString(direction,10));
      return tempVector;
    }

    double valueAt(double x) {
      return function.valueAt(vectorOf(x));
    }

    double derivativeAt(double x) {
      double[] g = function.derivativeAt(vectorOf(x));
      double d = 0.0;
      for (int j = 0; j < g.length; j++) {
        d += g[j] * direction[j];
      }
      return d;
    }

    OneDimDiffFunction(DiffFunction function, double[] initial, double[] direction) {
      this.function = function;
      this.initial = copyArray(initial);
      this.direction = copyArray(direction);
      this.tempVector = new double[function.domainDimension()];
    }

  } // end class OneDimDiffFunction


  // constants
  private static final double GOLD = 1.618034;
  private static final double GLIMIT = 100.0;
  private static final double TINY = 1.0e-20;

  private static Triple mnbrak(Triple abc, OneDimDiffFunction function) {
    // inputs
    double ax = abc.a;
    double fa = function.valueAt(ax);

    double bx = abc.b;
    double fb = function.valueAt(bx);

    if (fb > fa) {
      // swap
      double temp = fa;
      fa = fb;
      fb = temp;
      temp = ax;
      ax = bx;
      bx = temp;
    }

    // guess cx
    double cx = bx + GOLD * (bx - ax);
    double fc = function.valueAt(cx);

    // loop until we get a bracket
    while (fb > fc) {
      double r = (bx - ax) * (fb - fc);
      double q = (bx - cx) * (fb - fa);
      double u = bx - ((bx - cx) * q - (bx - ax) * r) / (2.0 * sign(fmax(fabs(q - r), TINY), q - r));
      double fu;
      double ulim = bx + GLIMIT * (cx - bx);
      if ((bx - u) * (u - cx) > 0.0) {
        fu = function.valueAt(u);
        if (fu < fc) {
          //Ax = new Double(bx);
          //Bx = new Double(u);
          //Cx = new Double(cx);
          //System.err.println("\nReturning3: a="+bx+" ("+fb+") b="+u+"("+fu+") c="+cx+" ("+fc+")");
          return new Triple(bx, u, cx);
        } else if (fu > fb) {
          //Cx = new Double(u);
          //Ax = new Double(ax);
          //Bx = new Double(bx);
          //System.err.println("\nReturning2: a="+ax+" ("+fa+") b="+bx+"("+fb+") c="+u+" ("+fu+")");
          return new Triple(ax, bx, u);
        }
        u = cx + GOLD * (cx - bx);
        fu = function.valueAt(u);
      } else if ((cx - u) * (u - ulim) > 0.0) {
        fu = function.valueAt(u);
        if (fu < fc) {
          bx = cx;
          cx = u;
          u = cx + GOLD * (cx - bx);
          fb = fc;
          fc = fu;
          fu = function.valueAt(u);
        }
      } else if ((u - ulim) * (ulim - cx) >= 0.0) {
        u = ulim;
        fu = function.valueAt(u);
      } else {
        u = cx + GOLD * (cx - bx);
        fu = function.valueAt(u);
      }
      ax = bx;
      bx = cx;
      cx = u;
      fa = fb;
      fb = fc;
      fc = fu;
    }
    //System.err.println("\nReturning: a="+ax+" ("+fa+") b="+bx+"("+fb+") c="+cx+" ("+fc+")");
    return new Triple(ax, bx, cx);
  }

  private static double dbrent(OneDimDiffFunction function, double ax, double bx, double cx) {
    // constants
    final boolean dbVerbose = false;
    final int ITMAX = 100;
    final double TOL = 1.0e-4;

    boolean ok1, ok2;
    double d = 0.0, d1, d2, du, e = 0.0;
    double fu, olde, tol1, tol2, u, u1, u2, xm;

    double a = (ax < cx ? ax : cx);
    double b = (ax > cx ? ax : cx);
    double x = bx;
    double v = bx;
    double w = bx;
    double fx = function.valueAt(x);
    double fv = fx;
    double fw = fx;
    double dx = function.derivativeAt(x);
    double dv = dx;
    double dw = dx;
    for (int iteration = 0; iteration < ITMAX; iteration++) {
      //System.err.println("dbrent "+iteration+" x "+x+" fx "+fx);
      xm = 0.5 * (a + b);
      tol1 = TOL * fabs(x); //+ZEPS (was 1e-10);
      tol2 = 2.0 * tol1;
      if (fabs(x - xm) <= (tol2 - 0.5 * (b - a))) {
        if (dbVerbose) {
          System.err.println("dbrent returning because min is cornered " + a + " (" + function.valueAt(a) + ") ~ " + x + " (" + fx + ") " + b + " (" + function.valueAt(b) + ")");
        }
        return x;
      }
      if (fabs(e) > tol1) {
        d1 = 2.0 * (b - a);
        d2 = d1;
        if (dw != dx) {
          d1 = (w - x) * dx / (dx - dw);
        }
        if (dv != dx) {
          d2 = (v - x) * dx / (dx - dv);
        }
        u1 = x + d1;
        u2 = x + d2;
        ok1 = ((a - u1) * (u1 - b) > 0.0 && dx * d1 <= 0.0);
        ok2 = ((a - u2) * (u2 - b) > 0.0 && dx * d2 <= 0.0);
        olde = e;
        e = d;
        if (ok1 || ok2) {
          if (ok1 && ok2) {
            d = (fabs(d1) < fabs(d2) ? d1 : d2);
          } else if (ok1) {
            d = d1;
          } else {
            d = d2;
          }
          if (fabs(d) <= fabs(0.5 * olde)) {
            u = x + d;
            if (u - a < tol2 || b - u < tol2) {
              d = sign(tol1, xm - x);
            }
          } else {
            e = (dx >= 0.0 ? a - x : b - x);
            d = 0.5 * e;
          }
        } else {
          e = (dx >= 0.0 ? a - x : b - x);
          d = 0.5 * e;
        }
      } else {
        e = (dx >= 0.0 ? a - x : b - x);
        d = 0.5 * e;
      }
      if (fabs(d) >= tol1) {
        u = x + d;
        fu = function.valueAt(u);
      } else {
        u = x + sign(tol1, d);
        fu = function.valueAt(u);
        if (fu > fx) {
          if (dbVerbose) {
            System.err.println("dbrent returning because derivative is broken");
          }
          return x;
        }
      }
      du = function.derivativeAt(u);
      if (fu <= fx) {
        if (u >= x) {
          a = x;
        } else {
          b = x;
        }
        v = w;
        fv = fw;
        dv = dw;
        w = x;
        fw = fx;
        dw = dx;
        x = u;
        fx = fu;
        dx = du;
      } else {
        if (u < x) {
          a = u;
        } else {
          b = u;
        }
        if (fu <= fw || w == x) {
          v = w;
          fv = fw;
          dv = dw;
          w = u;
          fw = fu;
          dw = du;
        } else if (fu < fv || v == x || v == w) {
          v = u;
          fv = fu;
          dv = du;
        }
      }
    }
    // dan's addition:
    if (fx < function.valueAt(0.0)) {
      return x;
    }
    if (dbVerbose) {
      System.err.println("Warning: exiting dbrent because ITMAX exceeded!");
    }
    return 0.0;
  }

  private static class Triple {
    public double a;
    public double b;
    public double c;

    public Triple(double a, double b, double c) {
      this.a = a;
      this.b = b;
      this.c = c;
    }

  }

  //public double lastXx = 1.0;
  double[] lineMinimize(DiffFunction function, double[] initial, double[] direction) {
    // make a 1-dim function along the direction line
    // THIS IS A HACK (but it's the NRiC peoples' hack)
    OneDimDiffFunction oneDim = new OneDimDiffFunction(function, initial, direction);
    // do a 1-dim line min on this function
    //Double Ax = new Double(0.0);
    //Double Xx = new Double(1.0);
    //Double Bx = new Double(0.0);
    // bracket the extreme pt
    double guess = 0.01;
    //System.err.println("Current "+oneDim.valueAt(0)+" nudge "+(oneDim.smallestZeroPositiveLocation()*1e-2)+" "+oneDim.valueAt(oneDim.smallestZeroPositiveLocation()*1e-5));
    if (!silent) {
      System.err.print("[");
    }
    Triple bracketing = mnbrak(new Triple(0, guess, 0), oneDim);
    if (!silent) {
      System.err.print("]");
    }
    double ax = bracketing.a;
    double xx = bracketing.b;
    double bx = bracketing.c;
    //lastXx = xx;
    // CHECK FOR END OF WORLD
    if (!(ax <= xx && xx <= bx) && !(bx <= xx && xx <= ax)) {
      System.err.println("Bad bracket order!");
    }
    if (verbose) {
      System.err.println("Bracketing found: " + ax + " " + xx + " " + bx);
      System.err.println("Bracketing found: " + oneDim.valueAt(ax) + " " + oneDim.valueAt(xx) + " " + oneDim.valueAt(bx));
      //System.err.println("Bracketing found: "+arrayToString(oneDim.vectorOf(ax),3)+" "+arrayToString(oneDim.vectorOf(xx),3)+" "+arrayToString(oneDim.vectorOf(bx),3));
    }
    // find the extreme pt
    if (!silent) {
      System.err.print("<");
    }
    double xmin = dbrent(oneDim, ax, xx, bx);
    if (!silent) {
      System.err.print(">");
    }
    // return the full vector
    //System.err.println("Went "+xmin+" during lineMinimize");
    return oneDim.vectorOf(xmin);
  }

  public double[] minimize(DiffFunction function, double functionTolerance, double[] initial) {
    return minimize(function, functionTolerance, initial, ITMAX);
  }

  public double[] minimize(DiffFunction dfunction, double functionTolerance, double[] initial, int maxIterations) {
    // check for derivatives

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

    // make some vectors
    double[] g = new double[dimension];
    double[] h = new double[dimension];
    double[] p = new double[dimension];
    for (int j = 0; j < dimension; j++) {
      g[j] = -xi[j];
      xi[j] = g[j];
      h[j] = g[j];
      p[j] = initial[j];
    }

    // iterations
    boolean simpleGDStep = false;
    for (int iterations = 1; iterations < maxIterations; iterations++) {

      if (!silent) {
        System.err.print("Iter " + iterations + " ");
      }
      // do a line min along descent direction
      //System.err.println("Minimizing from ("+p[0]+","+p[1]+") along ("+xi[0]+","+xi[1]+")\n");
      if (verbose) {
        System.err.println("Minimizing along " + arrayToString(xi, numToPrint));
      }
      //System.err.println("Current is "+fp);
      double[] p2 = lineMinimize(dfunction, p, xi);
      double fp2 = dfunction.valueAt(p2);
      //System.err.println("Result is "+fp2+" (from "+fp+") at ("+p2[0]+","+p2[1]+")\n");
      if (verbose) {
        System.err.println("Result is " + fp2 + " after " + iterations);
        System.err.println("Result at " + arrayToString(p2, numToPrint));
      }
      //System.err.print(fp2+"|"+(int)(Math.log((fabs(fp2-fp)+1e-100)/(fabs(fp)+fabs(fp2)+1e-100))/Math.log(10)));
      if (!silent) {
        System.err.printf(" %s (delta: %s)\n",
          nf.format(fp2), nf.format(fp-fp2));
      }
      if (monitor != null) {
        double monitorReturn = monitor.valueAt(p2);
        if (monitorReturn < functionTolerance) {
          return p2;
        }
      }
      // check convergence
      if (2.0 * fabs(fp2 - fp) <= functionTolerance * (fabs(fp2) + fabs(fp) + EPS)) {
        // convergence
        if (!checkSimpleGDConvergence || simpleGDStep || simpleGD) {
          return p2;
        }
        simpleGDStep = true;
        //System.err.println("Switched to GD for a step.");
      } else {
        //if (!simpleGD)
        //System.err.println("Switching to CGD.");
        simpleGDStep = false;
      }
      // shift variables
      for (int j = 0; j < dimension; j++) {
        xi[j] = p2[j] - p[j];
        p[j] = p2[j];
      }
      fp = fp2;
      // find the new gradient
      xi = copyArray(dfunction.derivativeAt(p));
      //System.err.print("mx "+arrayMax(xi)+" mn "+arrayMin(xi));

      if (!simpleGDStep && !simpleGD && (iterations % resetFrequency != 0)) {
        // do the magic -- part i
        // (calculate some dot products we'll need)
        double dgg = 0.0;
        double gg = 0.0;
        for (int j = 0; j < dimension; j++) {
          // g dot g
          gg += g[j] * g[j];
          // grad dot grad
          // FR method is:
          // dgg += x[j]*x[j];
          // PR method is:
          dgg += (xi[j] + g[j]) * xi[j];
        }

        // check for miraculous convergence
        if (gg == 0.0) {
          return p;
        }

        // magic part ii
        // (update the sequence in a way that tries to preserve conjugacy)
        double gam = dgg / gg;
        for (int j = 0; j < dimension; j++) {
          g[j] = -xi[j];
          h[j] = g[j] + gam * h[j];
          xi[j] = h[j];
        }
      } else {
        // miraculous simpleGD convergence
        double xixi = 0.0;
        for (int j = 0; j < dimension; j++) {
          xixi += xi[j] * xi[j];
        }
        // reset cgd
        for (int j = 0; j < dimension; j++) {
          g[j] = -xi[j];
          xi[j] = g[j];
          h[j] = g[j];
        }
        if (xixi == 0.0) {
          return p;
        }
      }
    }

    // too many iterations
    System.err.println("Warning: exiting minimize because ITER exceeded!");
    return p;

  }

  /**
   * Basic constructor, use this.
   */
  public CGMinimizer() {
    this(true);
  }

  /**
   * Pass in <code>false</code> to get per-iteration progress reports
   * (to stderr).
   *
   * @param silent a <code>boolean</code> value
   */
  public CGMinimizer(boolean silent) {
    this.silent = silent;
  }

  /**
   * Perform minimization with monitoring.  After each iteration,
   * monitor.valueAt(x) gets called, with the double array <code>x</code>
   * being that iteration's ending point.  A return <code>&lt;
   * tol</code> forces convergence (terminates the CG procedure).
   * Specially for Kristina.
   *
   * @param monitor a <code>Function</code> value
   */
  public CGMinimizer(Function monitor) {
    this();
    this.monitor = monitor;
  }

}
