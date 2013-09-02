package edu.stanford.nlp.optimization;

import java.util.Arrays;

/**
 * A constraint for the {@link PenaltyConstrainedMinimizer} that causes
 * the components to sum to 1.
 *
 * @author Dan Klein
 */
public class SumToOneConstraint implements DiffFunction {
  private int dimension;

  public SumToOneConstraint(int dimension) {
    this.dimension = dimension;
  }

  public int domainDimension() {
    return dimension;
  }

  public double valueAt(double[] x) {
    double sum = 0.0;
    for (int i = 0; i < dimension; i++) {
      sum += x[i];
    }
    return sum - 1.0;
  }

  public double[] derivativeAt(double[] x) {
    double[] deriv = new double[domainDimension()];
    Arrays.fill(deriv, 1.0);
    return deriv;
  }
}
