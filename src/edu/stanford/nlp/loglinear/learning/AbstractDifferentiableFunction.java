package edu.stanford.nlp.loglinear.learning;

import edu.stanford.nlp.loglinear.model.ConcatVector;

/**
 * Created by keenon on 8/26/15.
 *
 * This provides a separation between the functions and optimizers, that lets us test optimizers more effectively by
 * generating convex functions that are solvable in closed form, then checking the optimizer arrives at the same
 * solution.
 */
public abstract class AbstractDifferentiableFunction<T> {
    /**
     * A little struct for passing back the important results of a gradient/value calculation
     */
    public static class FunctionSummaryAtPoint {
        public double value;
        public ConcatVector gradient;

        public FunctionSummaryAtPoint(double value, ConcatVector gradient) {
            this.value = value;
            this.gradient = gradient;
        }
    }

    /**
     * Gets a summary of the function of a singe data instance at a single point
     *
     * @param dataPoint the data point we want a summary for
     * @param weights the weights to use
     * @return the gradient and value of the function at this point
     */
    public abstract FunctionSummaryAtPoint getSummaryForInstance(T dataPoint, ConcatVector weights);
}
