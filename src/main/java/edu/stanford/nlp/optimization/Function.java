package edu.stanford.nlp.optimization;

/**
 * An interface for double-valued functions over double arrays.
 *
 * @author <a href="mailto:klein@cs.stanford.edu">Dan Klein</a>
 * @version 1.0
 * @since 1.0
 */
public interface Function {
  /**
   * Returns the value of the function at a single point.
   *
   * @param x a {@code double[]} input
   * @return the function value at the input
   */
  double valueAt(double[] x);

  /**
   * Returns the number of dimensions in the function's domain
   *
   * @return the number of domain dimensions
   */
  int domainDimension();
}
