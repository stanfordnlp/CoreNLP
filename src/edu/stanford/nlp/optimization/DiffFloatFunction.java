package edu.stanford.nlp.optimization;

/**
 * An interface for once-differentiable double-valued functions over
 * double arrays.  NOTE: it'd be good to have an AbstractDiffFunction
 * that wrapped a Function with a finite-difference approximation.
 *
 * @author <a href="mailto:klein@cs.stanford.edu">Dan Klein</a>
 * @version 1.0
 * @see Function
 * @since 1.0
 */
public interface DiffFloatFunction extends FloatFunction {
  /**
   * Returns the first-derivative vector at the input location.
   *
   * @param x a <code>double[]</code> input vector
   * @return the vector of first partial derivatives.
   */
  float[] derivativeAt(float[] x);
}
