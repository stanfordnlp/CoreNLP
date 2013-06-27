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
public interface DiffFunction extends Function {
  /**
   * Returns the first-derivative vector at the input location.
   *
   * @param x a <code>double[]</code> input vector
   * @return the vector of first partial derivatives.
   */
  double[] derivativeAt(double[] x);
}
