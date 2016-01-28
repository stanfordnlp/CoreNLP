package edu.stanford.nlp.loglinear.learning;

import edu.stanford.nlp.loglinear.model.ConcatVector;

/**
 * This interface assumes that users will want to pass in data one at a time, and that they will want access to weights
 * at any time. The interface makes no promises about when or how updates will happen, that is up to subclasses to work
 * out.
 *
 * @author keenon
 */
public abstract class AbstractOnlineOptimizer<T> {
  /**
   * This is the only thing that online optimizers need to do in order to work. Update as the result of a new data
   * point. How they do that is up to the optimizer.
   *
   * @param datum the piece of labeled data to use for a gradient step
   */
  public abstract void updateWeights(T datum);

  /**
   * Gets a cloned ConcatVector representing the current weights.
   *
   * @return the current weights
   */
  public abstract ConcatVector getWeights();
}
