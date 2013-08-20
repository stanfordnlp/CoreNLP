package edu.stanford.nlp.optimization;

/**
 * Example of using the minimization classes.  The class contents is included
 * here in the documentation to make the example easy to see.
 * <p/>
 * <pre>
 * public class MinimizationExample {
 * <p/>
 * // Sum of squares objective
 * private static class SumSquaresFunction implements DiffFunction {
 * public int domainDimension() { return 4; }
 * public double valueAt(double[] x) {
 * double sum = 0.0;
 * for (int i=0; i<domainDimension(); i++) {
 * sum += x[i]*x[i];
 * }
 * return sum;
 * }
 * <p/>
 * public double[] derivativeAt(double[] x) {
 * double[] deriv = new double[domainDimension()];
 * for (int i=0; i<domainDimension(); i++) {
 * deriv[i] = 2.0*x[i];
 * }
 * return deriv;
 * }
 * }
 * <p/>
 * // Sum to one constraint
 * private static class SumToOneConstraint implements DiffFunction {
 * public int domainDimension() { return 4; }
 * public double valueAt(double[] x) {
 * double sum = 0.0;
 * for (int i=0; i<domainDimension(); i++) {
 * sum += x[i];
 * }
 * return sum - 1.0;
 * }
 * <p/>
 * public double[] derivativeAt(double[] x) {
 * double[] deriv = new double[domainDimension()];
 * for (int i=0; i<domainDimension(); i++) {
 * deriv[i] = 1.0;
 * }
 * return deriv;
 * }
 * }
 * <p/>
 * // Component minimum constraint
 * private static class ComponentMinimumConstraint implements DiffFunction {
 * private int component;
 * private double minimum;
 * <p/>
 * public int domainDimension() { return 4; }
 * public double valueAt(double[] x) {
 * return x[component] - minimum;
 * }
 * public double[] derivativeAt(double[] x) {
 * double[] deriv = new double[domainDimension()];
 * for (int i=0; i<domainDimension(); i++) {
 * deriv[i] = 0.0;
 * }
 * deriv[component] = 1.0;
 * return deriv;
 * <p/>
 * }
 * public ComponentMinimumConstraint(int component, double minimum) {
 * this.component = component;
 * this.minimum = minimum;
 * }
 * }
 * <p/>
 * private static String arrayToString(double[] x) {
 * StringBuffer sb = new StringBuffer("(");
 * for (int j=0; j<x.length; j++) {
 * sb.append(x[j]);
 * if (j != x.length-1)
 * sb.append(", ");
 * }
 * sb.append(")");
 * return sb.toString();
 * }
 * <p/>
 * public static void main(String[] args) {
 * // Set up solvers...
 * Minimizer unconstrainedMinimizer = new CGMinimizer();
 * ConstrainedMinimizer constrainedMinimizer = new PenaltyConstrainedMinimizer(unconstrainedMinimizer);
 * <p/>
 * // Set up problem...
 * DiffFunction objective = new SumSquaresFunction(); // sum xi^2
 * <p/>
 * DiffFunction[] eqConstraints = new DiffFunction[1];
 * eqConstraints[0] = new SumToOneConstraint(); // sum xi = 1
 * <p/>
 * DiffFunction[] ineqConstraints = new DiffFunction[2];
 * ineqConstraints[0] = new ComponentMinimumConstraint(1,0.3); // x1 >= 0.3
 * ineqConstraints[1] = new ComponentMinimumConstraint(2,0.5); // x2 >= 0.5
 * <p/>
 * DiffFunction[] noConstraints = new DiffFunction[0];
 * <p/>
 * double[] initial = {0.25, 0.25, 0.25, 0.25}; // note that it's not actual feasible
 * <p/>
 * // unconstrained minimization
 * double[] x = unconstrainedMinimizer.minimize(objective, 1e-4, initial);
 * System.out.println("Unconstrained minimum is :            "+objective.valueAt(x)+" at "+arrayToString(x));
 * <p/>
 * // with equality constraints
 * double[] xEQ = constrainedMinimizer.minimize(objective, 1e-4, eqConstraints, 1e-8, noConstraints, 0.0, initial);
 * System.out.println("Equality-conconstrained minimum is :  "+objective.valueAt(xEQ)+" at "+arrayToString(xEQ));
 * <p/>
 * // with inequality constraints
 * double[] xINEQ = constrainedMinimizer.minimize(objective, 1e-4, noConstraints, 0, ineqConstraints, 1e-8, initial);
 * System.out.println("Inequality-conconstrained minimum is :"+objective.valueAt(xINEQ)+" at "+arrayToString(xINEQ));
 * <p/>
 * // with all constraints
 * double[] xBOTH = constrainedMinimizer.minimize(objective, 1e-4, eqConstraints, 1e-8, ineqConstraints, 1e-8, initial);
 * System.out.println("Mixed-conconstrained minimum is :     "+objective.valueAt(xBOTH)+" at "+arrayToString(xBOTH));
 * <p/>
 * }
 * }
 * </pre>
 *
 * @author <a href="mailto:klein@cs.stanford.edu">Dan Klein</a>
 * @version 1.0
 * @since 1.0
 */
public class MinimizationExample {

  // Sum of squares objective
  private static class SumSquaresFunction implements DiffFunction {
    public int domainDimension() {
      return 4;
    }

    public double valueAt(double[] x) {
      double sum = 0.0;
      for (int i = 0; i < domainDimension(); i++) {
        sum += x[i] * x[i];
      }
      return sum;
    }

    public double[] derivativeAt(double[] x) {
      double[] deriv = new double[domainDimension()];
      for (int i = 0; i < domainDimension(); i++) {
        deriv[i] = 2.0 * x[i];
      }
      return deriv;

    }
  }


  private static String arrayToString(double[] x) {
    StringBuffer sb = new StringBuffer("(");
    for (int j = 0; j < x.length; j++) {
      sb.append(x[j]);
      if (j != x.length - 1) {
        sb.append(", ");
      }
    }
    sb.append(")");
    return sb.toString();
  }

  public static void main(String[] args) {
    // Set up solvers...
    Minimizer unconstrainedMinimizer = new CGMinimizer();
    ConstrainedMinimizer constrainedMinimizer = new PenaltyConstrainedMinimizer(unconstrainedMinimizer);

    // Set up problem...
    DiffFunction objective = new SumSquaresFunction(); // sum xi^2

    DiffFunction[] eqConstraints = new DiffFunction[1];
    eqConstraints[0] = new SumToOneConstraint(4); // sum xi = 1

    DiffFunction[] ineqConstraints = new DiffFunction[2];
    ineqConstraints[0] = new ComponentMinimumConstraint(4, 1, 0.3); // x1 >= 0.3
    ineqConstraints[1] = new ComponentMinimumConstraint(4, 2, 0.5); // x2 >= 0.5

    DiffFunction[] noConstraints = new DiffFunction[0];

    double[] initial = {0.5, 0.5, 0.5, 0.5}; // note that it's not actual feasible

    // unconstrained minimization
    double[] x = unconstrainedMinimizer.minimize(objective, 1e-4, initial);
    System.out.println("Unconstrained minimum is :            " + objective.valueAt(x) + " at " + arrayToString(x));

    // with equality constraints
    double[] xEQ = constrainedMinimizer.minimize(objective, 1e-4, eqConstraints, 1e-8, noConstraints, 0.0, initial);
    System.out.println("Equality-conconstrained minimum is :  " + objective.valueAt(xEQ) + " at " + arrayToString(xEQ));
    
    // with inequality constraints
    double[] xINEQ = constrainedMinimizer.minimize(objective, 1e-4, noConstraints, 0, ineqConstraints, 1e-8, initial);
    System.out.println("Inequality-conconstrained minimum is :" + objective.valueAt(xINEQ) + " at " + arrayToString(xINEQ));
    
    // with all constraints
    double[] xBOTH = constrainedMinimizer.minimize(objective, 1e-4, eqConstraints, 1e-8, ineqConstraints, 1e-8, initial);
    System.out.println("Mixed-conconstrained minimum is :     " + objective.valueAt(xBOTH) + " at " + arrayToString(xBOTH));

  }
}
