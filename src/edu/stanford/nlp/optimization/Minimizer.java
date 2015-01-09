package edu.stanford.nlp.optimization;

/**
 * The interface for unconstrained function minimizers.
 *
 * Implementations may also vary in their requirements for the
 * arguments.  For example, implementations may or may not care if the
 * {@code initial} feasible vector turns out to be non-feasible
 * (or {@code null}!).  Similarly, some methods may insist that objectives
 * and/or constraint {@code Function} objects actually be
 * {@code DiffFunction} objects.
 *
 * @author <a href="mailto:klein@cs.stanford.edu">Dan Klein</a>
 * @version 1.0
 * @since 1.0
 */
public interface Minimizer<T extends Function> {

  /**
   * Attempts to find an unconstrained minimum of the objective
   * {@code function} starting at {@code initial}, accurate to
   * within {@code functionTolerance} (normally implemented as
   * a multiplier of the range value to give range tolerance).
   *
   * @param function          The objective function
   * @param functionTolerance A {@code double} value
   * @param initial           An initial feasible point
   * @return Unconstrained minimum of function
   */
  double[] minimize(T function, double functionTolerance, double[] initial);


  /**
   * Attempts to find an unconstrained minimum of the objective
   * {@code function} starting at {@code initial}, accurate to
   * within {@code functionTolerance} (normally implemented as
   * a multiplier of the range value to give range tolerance), but
   * running only for at most {@code maxIterations} iterations.
   *
   * @param function          The objective function
   * @param functionTolerance A {@code double} value
   * @param initial           An initial feasible point
   * @param maxIterations     Maximum number of iterations
   * @return Unconstrained minimum of function
   */
  double[] minimize(T function, double functionTolerance, double[] initial, int maxIterations);

}
