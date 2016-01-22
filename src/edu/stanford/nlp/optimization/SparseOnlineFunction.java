package edu.stanford.nlp.optimization;
import edu.stanford.nlp.stats.Counter;

/**
 * An interface for functions over sparse parameters.
 * the point is to run this online, and data control is passed to the optimizer
 * K is probably a String or an int
 * selectedData are the data points used in the current evaluation, 
 * which is more naturally handled by the minimizers instead of the implementation
 * though if one prefers, one can implement that elsewhere, and make valueAt,
 * derivativeAt independent of selectedData
 * @author <a href="mailto:sidaw@cs.stanford.edu">Sida Wang</a>
 * @version 1.0
 * @since 1.0
 */
public interface SparseOnlineFunction<K> {
  /**
   * Returns the value of the function at a single point.
   *
   * @param x a <code>double[]</code> input
   * @return the function value at the input
   */
  double valueAt(Counter<K> x, int[] selectedData);
  Counter<K> derivativeAt(Counter<K> x, int[] selectedData);
  
  // return the size of the data, return -1 if you want to handle data selection yourself
  int dataSize();
}
