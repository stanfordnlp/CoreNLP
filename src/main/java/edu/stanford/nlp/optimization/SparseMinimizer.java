package edu.stanford.nlp.optimization;

import edu.stanford.nlp.stats.Counter;

/**
 * The interface for unconstrained function minimizers with sparse parameters
 * like Minimizer, except with sparse parameters
 *
 * @author <a href="mailto:sidaw@cs.stanford.edu">Sida Wang</a>
 * @version 1.0
 * @since 1.0
 */
public interface SparseMinimizer<K, T extends SparseOnlineFunction<K> > {
  /**
   * Attempts to find an unconstrained minimum of the objective
   * <code>function</code> starting at <code>initial</code>, within
   * <code>functionTolerance</code>.
   *
   * @param function          the objective function
   * @param initial           a initial feasible point
   * @return Unconstrained minimum of function
   */
  Counter<K> minimize(T function, Counter<K> initial);
  Counter<K> minimize(T function, Counter<K> initial, int maxIterations);
}
