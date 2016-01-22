package edu.stanford.nlp.optimization;

import java.util.Set;

/**
 * Indicates that an minimizer supports evaluation periodically
 *
 * @author Mengqiu Wang
 */
public interface HasRegularizerParamRange {
  public Set<Integer> getRegularizerParamRange(double[] x);
}
