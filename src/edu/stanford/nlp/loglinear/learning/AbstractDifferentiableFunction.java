package edu.stanford.nlp.loglinear.learning;

import edu.stanford.nlp.loglinear.model.ConcatVector;

/**
 * Created on 8/26/15.
 * @author keenon
 * <p>
 * This provides a separation between the functions and optimizers, that lets us test optimizers more effectively by
 * generating convex functions that are solvable in closed form, then checking the optimizer arrives at the same
 * solution.
 */
public abstract class AbstractDifferentiableFunction<T> {
  /**
   * Gets a summary of the function of a singe data instance at a single point
   *
   * @param dataPoint the data point we want a summary for
   * @param weights   the weights to use
   * @param gradient  the gradient to use, will be updated by accumulating the gradient from this instance
   * @return value of the function at this point
   */
  public abstract double getSummaryForInstance(T dataPoint, ConcatVector weights, ConcatVector gradient);
}
