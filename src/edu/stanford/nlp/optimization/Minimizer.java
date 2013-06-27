package edu.stanford.nlp.optimization;

/**
 * The interface for unconstrained function minimizers.
 * <p/>
 * Implementations may also vary in their requirements for the
 * arguments.  For example, implementations may or may not care if the
 * <code>initial</code> feasible vector turns out to be non-feasible
 * (or null!).  Similarly, some methods may insist that objectives
 * and/or constraint <code>Function</code> objects actually be
 * <code>DiffFunction</code> objects.
 *
 * @author <a href="mailto:klein@cs.stanford.edu">Dan Klein</a>
 * @version 1.0
 * @since 1.0
 */
public interface Minimizer<T extends Function> {

  /**
   * Attempts to find an unconstrained minimum of the objective
   * <code>function</code> starting at <code>initial</code>, within
   * <code>functionTolerance</code>.
   *
   * @param function          the objective function
   * @param functionTolerance a <code>double</code> value
   * @param initial           a initial feasible point
   * @return Unconstrained minimum of function
   */
  double[] minimize(T function, double functionTolerance, double[] initial);
  double[] minimize(T function, double functionTolerance, double[] initial, int maxIterations);

}
