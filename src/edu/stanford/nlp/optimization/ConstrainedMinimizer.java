package edu.stanford.nlp.optimization;

/**
 * The interface for constrained function minimizers.
 * <p/>
 * Not all cases need to be supported by all implementations.  For
 * example, in implementation might support inequality constraints
 * only.
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
public interface ConstrainedMinimizer<T extends Function> extends Minimizer<T> {

  /**
   * The general case, allowing both equality and inequality
   * constraints.  A given implementation can throw an
   * UnsupportedOperationException if it cannot handle the constraints
   * it is supplied.
   * <p/>
   * It is expected that <code>minimize(function, functionTolerance,
   * [], 0, [], 0, initial)</code> behave exactly like the
   * <code>minimize(function, functionTolerance, initial)</code>
   * method inherited from <code>Minimizer</code>.
   *
   * @param function                the objective function
   * @param functionTolerance       a <code>double</code> value
   * @param eqConstraints           an array of zero or more equality constraints
   * @param eqConstraintTolerance   the violation tolerace for equality constraints
   * @param ineqConstraints         an array of zero or more inequality constraints
   * @param ineqConstraintTolerance the violation tolerace for equality constraints
   * @param initial                 a initial feasible (!) point
   * @return a minimizing feasible point
   */
  double[] minimize(T function, double functionTolerance, Function[] eqConstraints, double eqConstraintTolerance, Function[] ineqConstraints, double ineqConstraintTolerance, double[] initial);

}
