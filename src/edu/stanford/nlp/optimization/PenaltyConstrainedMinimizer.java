package edu.stanford.nlp.optimization;

/**
 * A wrapper class which builds a constrained minimizer out of an
 * unconstrained one by finding the unconstrained minimum of a
 * sequence of penalized surfaces.  It assumes that the objective it
 * is given is a <code>DiffFunction</code>, even if the base
 * unconstrained minimzer just ignores the derivative information.
 * <p/>
 * The desired unconstrained minimizer is passed in on construction:
 * <p/>
 * <code>Minimizer m = new SomeUnconstrainedMinimizer();</code>
 * <code>ConstrainedMinimizer cm = new PenaltyConstrainedMinimizer(m);</code>
 *
 * @author <a href="mailto:klein@cs.stanford.edu">Dan Klein</a>
 * @version 1.0
 * @see ConstrainedMinimizer
 * @since 1.0
 */
public class PenaltyConstrainedMinimizer implements ConstrainedMinimizer<DiffFunction> {
  private Minimizer<DiffFunction> minimizer;

  private static final boolean silent = true;

  class PenalizedFunction implements DiffFunction {
    DiffFunction function;

    DiffFunction[] eqConstraints;
    double[] eqLagrange;
    double[] eqPenalties;

    DiffFunction[] ineqConstraints;
    double[] ineqLagrange;
    double[] ineqPenalties;

    public int domainDimension() {
      return function.domainDimension();
    }

    public double valueAt(double[] x) {
      double val = 0.0;
      val += function.valueAt(x);
      // equality constraints
      for (int i = 0; i < eqConstraints.length; i++) {
        double c = eqConstraints[i].valueAt(x);
        val += eqLagrange[i] * c;
        val += 0.5 * eqPenalties[i] * c * c;
      }
      // inequality constraints
      for (int i = 0; i < ineqConstraints.length; i++) {
        double c = ineqConstraints[i].valueAt(x);
        //val += Math.exp(-1.0*ineqPenalties[i]*c);
        double tmp = fmax(c, 2.0 * c + ineqLagrange[i] / ineqPenalties[i]);
        val += ineqLagrange[i] * tmp;
        val += 0.5 * ineqPenalties[i] * tmp * tmp;
        //val += (tmp2*tmp2 - ineqLagrange[i]*ineqLagrange[i])/2.0*ineqPenalties[i];
      }
      return val;
    }

    public double[] derivativeAt(double[] x) {
      double[] deriv = copyArray(function.derivativeAt(x));
      // equality constraints
      for (int i = 0; i < eqConstraints.length; i++) {
        double[] cDeriv = eqConstraints[i].derivativeAt(x);
        double cVal = eqConstraints[i].valueAt(x);
        for (int d = 0; d < domainDimension(); d++) {
          deriv[d] += eqLagrange[i] * cDeriv[d];
          deriv[d] += eqPenalties[i] * cVal * cDeriv[d];
        }
      }
      // inequality constraints
      for (int i = 0; i < ineqConstraints.length; i++) {
        double[] cDeriv = ineqConstraints[i].derivativeAt(x);
        double cVal = ineqConstraints[i].valueAt(x);
        for (int d = 0; d < domainDimension(); d++) {
          //deriv[d] += ineqLagrange[i]*cDeriv[d];
          //deriv[d] += -1.0*ineqPenalties[i]*Math.exp(-1.0*ineqPenalties[i]*cVal)*cDeriv[d];
          //deriv[d] += (cVal > 0 ? 0 : ineqPenalties[i]*cVal*cDeriv[d]);
          double tmp = cVal;
          double tmpD = cDeriv[d];
          double crit = cVal + ineqLagrange[i] / ineqPenalties[i];
          if (crit > 0) {
            tmp += crit;
            tmpD += cDeriv[d];
          }
          deriv[d] += ineqLagrange[i] * tmpD;
          deriv[d] += ineqPenalties[i] * tmp * tmpD;
        }
      }
      return deriv;
    }

    PenalizedFunction(Function function, Function[] eqConstraints, double[] eqLagrange, double[] eqPenalties, Function[] ineqConstraints, double[] ineqLagrange, double[] ineqPenalties) {
      this.function = (DiffFunction) function;
      this.eqConstraints = (DiffFunction[]) eqConstraints;
      this.eqLagrange = eqLagrange;
      this.eqPenalties = eqPenalties;
      this.ineqConstraints = (DiffFunction[]) ineqConstraints;
      this.ineqLagrange = ineqLagrange;
      this.ineqPenalties = ineqPenalties;
    }
  }

  static double[] copyArray(double[] a) {
    double[] result = new double[a.length];
    System.arraycopy(a, 0, result, 0, a.length);
    return result;
  }

  static private double arrayMax(double[] x) {
    double max = Double.NEGATIVE_INFINITY;
    for (double d : x) {
      if (max < d) {
        max = d;
      }
    }
    return max;
  }

  private static double fabs(double x) {
    if (x < 0) {
      return -1.0 * x;
    }
    return x;
  }

  private static double fmax(double x, double y) {
    if (x < y) {
      return y;
    }
    return x;
  }

  private static String arrayToString(double[] x) {
    StringBuilder sb = new StringBuilder("(");
    for (int j = 0; j < x.length; j++) {
      sb.append(x[j]);
      if (j != x.length - 1) {
        sb.append(", ");
      }
    }
    sb.append(")");
    return sb.toString();
  }

  public double[] minimize(DiffFunction function, double functionTolerance, Function[] eqConstraints, double eqConstraintTolerance, Function[] ineqConstraints, double ineqConstraintTolerance, double[] initial) {
    // int dimension = function.domainDimension();
    int numEqConstraints = eqConstraints.length;
    int numIneqConstraints = ineqConstraints.length;

    // check that the function is a DiffFunction
    if (!(function instanceof DiffFunction)) {
      throw new UnsupportedOperationException();
    }

    // check the constraints
    for (int i = 0; i < numEqConstraints; i++) {
      if (!(eqConstraints[i] instanceof DiffFunction)) {
        throw new UnsupportedOperationException();
      }
    }
    for (int i = 0; i < numIneqConstraints; i++) {
      if (!(ineqConstraints[i] instanceof DiffFunction)) {
        throw new UnsupportedOperationException();
      }
    }

    // use a penalty method to solve for the lagrange multipliers and the x

    double[] eqLagrange = new double[numEqConstraints];
    double[] eqPenalties = new double[numEqConstraints];
    for (int i = 0; i < numEqConstraints; i++) {
      eqLagrange[i] = 0;
      eqPenalties[i] = 1.0;
    }

    double[] ineqLagrange = new double[numIneqConstraints];
    double[] ineqPenalties = new double[numIneqConstraints];
    for (int i = 0; i < numIneqConstraints; i++) {
      ineqLagrange[i] = 0;
      ineqPenalties[i] = 1.0;
    }

    double worstEqViolation = 1.0 + 2.0 * eqConstraintTolerance;
    double worstIneqViolation = 1.0 + 2.0 * ineqConstraintTolerance;
    double lastWorstEqViolation = 10 * worstEqViolation;
    double lastWorstIneqViolation = 10 * worstIneqViolation;

    int iter = 0;

    double[] x = copyArray(initial);

    // do a series of penalized minimizations
    boolean lowTolIter = false;
    while (iter < 100 && (worstEqViolation > eqConstraintTolerance || worstIneqViolation > ineqConstraintTolerance || !lowTolIter)) {

      if (worstEqViolation <= eqConstraintTolerance && worstIneqViolation <= ineqConstraintTolerance) {
        lowTolIter = true;
      } else {
        lowTolIter = false;
      }

      // build a penalized surface
      DiffFunction penalizedFunction = new PenalizedFunction(function, eqConstraints, eqLagrange, eqPenalties, ineqConstraints, ineqLagrange, ineqPenalties);

      // minimize it
      double thisTol = (lowTolIter ? functionTolerance : Math.sqrt(functionTolerance));
      x = minimizer.minimize(penalizedFunction, thisTol, x);
      penalizedFunction.derivativeAt(x);

      // update lagrange multipliers and penalties

      // EQUALITY CONSTRAINTS
      lastWorstEqViolation = worstEqViolation;
      worstEqViolation = Double.NEGATIVE_INFINITY;
      double[] eqViolations = new double[numEqConstraints];
      double[] eqValues = new double[numEqConstraints];
      for (int i = 0; i < numEqConstraints; i++) {
        eqValues[i] = eqConstraints[i].valueAt(x);
        eqViolations[i] = fabs(eqValues[i]);
      }
      worstEqViolation = arrayMax(eqViolations);
      // update penalties and lagrange multipliers
      for (int i = 0; i < numEqConstraints; i++) {
        // lagrange multipliers should take over the forces previously
        // exerted by the penalty terms
        eqLagrange[i] += eqPenalties[i] * eqValues[i];
        // penalties increase more or less based on violation severity
        if (eqViolations[i] >= worstEqViolation / 2.0 && worstEqViolation >= eqConstraintTolerance) {
          eqPenalties[i] *= 1.0;//*= 1.2;
        } else {
          eqPenalties[i] *= 1.0;//*= 2.0;
        }
        // penalties also increase if constraint satisfaction is too slow
        if (worstEqViolation / (lastWorstEqViolation + 1e-100) > 0.25 && worstEqViolation >= eqConstraintTolerance) {
          eqPenalties[i] *= 5.0;
        }
      }

      if (!silent) {
        System.err.println("!e " + arrayMax(eqPenalties) + "/" + worstEqViolation + "!");
      }

      // INEQUALTIY CONSTRAINTS
      lastWorstIneqViolation = worstIneqViolation;
      worstIneqViolation = Double.NEGATIVE_INFINITY;
      double[] ineqViolations = new double[numIneqConstraints];
      double[] ineqValues = new double[numIneqConstraints];
      for (int i = 0; i < numIneqConstraints; i++) {
        // ineq violations can be < 0 constraint values OR
        //   >= 0 constraints with non-zero lagrange multipliers / penalties
        double cVal = ineqConstraints[i].valueAt(x);
        ineqValues[i] = cVal;
        ineqViolations[i] = ((cVal < 0 ? -1.0 * cVal : 0) + fabs(ineqLagrange[i] * cVal));
      }
      worstIneqViolation = fmax(0.0, arrayMax(ineqViolations));
      // update penalties and lagrange multipliers
      for (int i = 0; i < numIneqConstraints; i++) {
        // lagrange multipliers should take over the forces previously
        // exerted by the penalty terms ... sort of
        double crit = ineqValues[i] + ineqLagrange[i] / ineqPenalties[i];
        if (crit > 0) {
          ineqLagrange[i] += ineqPenalties[i] * crit;
        }
        ineqLagrange[i] += ineqPenalties[i] * ineqValues[i];
        if (ineqLagrange[i] > 0) {
          ineqLagrange[i] = 0;
        }
        // penalties increase more or less based on violation severity
        if (ineqViolations[i] >= worstIneqViolation / 2.0 && worstIneqViolation >= ineqConstraintTolerance) {
          ineqPenalties[i] *= 1.0;//+= 0.2;
        } else {
          ineqPenalties[i] *= 1.0;//+= 1.0;
        }
        // penalties also increase if constraint satisfaction is too slow
        if (worstIneqViolation / (lastWorstIneqViolation + 1e-100) > 0.25 && worstIneqViolation >= ineqConstraintTolerance) {
          ineqPenalties[i] *= 5.0;
        }
      }

      if (!silent) {
        System.err.println("!i " + arrayMax(ineqPenalties) + "/" + worstIneqViolation + "!");
      }

    }

    return copyArray(x);
  }

    public double[] minimize(DiffFunction function, double functionTolerance, double[] initial, int maxIterations) {
	return minimizer.minimize(function, functionTolerance, initial, maxIterations);
    }

  public double[] minimize(DiffFunction function, double functionTolerance, double[] initial) {
    return minimizer.minimize(function, functionTolerance, initial);
  }

  public PenaltyConstrainedMinimizer(Minimizer<DiffFunction> minimizer) {
    this.minimizer = minimizer;
  }
}
