package edu.stanford.nlp.optimization;

import java.util.Set;

/**
 * Indicates that an minimizer supports evaluation periodically
 *
 * @author Mengqiu Wang
 */
public interface HasL1ParamRange {
  public Set<Integer> getL1ParamRange(double[] x);
}
