package edu.stanford.nlp.optimization;

/**
 * A constraint for the {@link PenaltyConstrainedMinimizer} which causes a specific
 * component to be &gt;= some value.
 *
 * @author Dan Klein
 */
public class ComponentMinimumConstraint implements DiffFunction {
  private int dimension;
  private int component;
  private double minimum;

  public ComponentMinimumConstraint(int dimension, int component, double minimum) {
    this.dimension = dimension;
    this.component = component;
    this.minimum = minimum;
  }

  public int domainDimension() {
    return dimension;
  }

  public double valueAt(double[] x) {
    return x[component] - minimum;
  }

  public double[] derivativeAt(double[] x) {
    double[] deriv = new double[domainDimension()];
    for (int i = 0; i < domainDimension(); i++) {
      deriv[i] = 0.0;
    }
    deriv[component] = 1.0;
    return deriv;
  }
}
