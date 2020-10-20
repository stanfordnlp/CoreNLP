package edu.stanford.nlp.optimization;

/**
 * An interface for double-valued functions over double arrays.
 *
 * @author <a href="mailto:klein@cs.stanford.edu">Dan Klein</a>
 * @version 1.0
 * @since 1.0
 */
public interface FloatFunction {
  /**
   * Returns the value of the function at a single point.
   *
   * @param x a <code>double[]</code> input
   * @return the function value at the input
   */
  float valueAt(float[] x);

  /**
   * Returns the number of dimensions in the function's domain
   *
   * @return the number of domain dimensions
   */
  int domainDimension();
}
