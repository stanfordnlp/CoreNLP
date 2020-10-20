package edu.stanford.nlp.optimization;

import java.util.Set;

/**
 * Indicates that a Function should only be regularized on a subset
 * of its parameters.
 *
 * @author Mengqiu Wang
 */
public interface HasRegularizerParamRange {

  Set<Integer> getRegularizerParamRange(double[] x);

}
