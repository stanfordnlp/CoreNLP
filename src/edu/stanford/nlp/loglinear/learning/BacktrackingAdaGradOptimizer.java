package edu.stanford.nlp.loglinear.learning;

import edu.stanford.nlp.loglinear.model.ConcatVector;

/**
 * Created by keenon on 8/26/15.
 *
 * Handles optimizing an AbstractFunction through AdaGrad guarded by backtracking.
 */
public class BacktrackingAdaGradOptimizer extends AbstractBatchOptimizer {

    final static double alpha = 0.125;

    @Override
    public boolean updateWeights(ConcatVector weights, ConcatVector gradient, double logLikelihood, OptimizationState optimizationState) {
        AdaGradOptimizationState s = (AdaGradOptimizationState)optimizationState;

        double logLikelihoodChange = logLikelihood - s.lastLogLikelihood;

        if (logLikelihoodChange == 0) {
            System.err.println("\tlogLikelihood improvement = 0: quitting");
            return true;
        }

        // Check if we should backtrack

        else if (logLikelihoodChange < 0) {

            // If we should, move the weights back by half, and cut the lastDerivative by half

            s.lastDerivative.mapInPlace((d) -> d / 2);
            weights.addVectorInPlace(s.lastDerivative, -1.0);

            System.err.println("\tBACKTRACK...");

            // if the lastDerivative norm falls below a threshold, it means we've converged

            if (s.lastDerivative.dotProduct(s.lastDerivative) < 1.0e-9) {
                System.err.println("\tBacktracking derivative norm "+s.lastDerivative.dotProduct(s.lastDerivative)+" < 1.0e-9: quitting");
                return true;
            }
        }

        // Apply AdaGrad

        else {
            ConcatVector squared = gradient.deepClone();
            squared.mapInPlace((d) -> d * d);
            s.adagradAccumulator.addVectorInPlace(squared, 1.0);

            ConcatVector sqrt = s.adagradAccumulator.deepClone();
            sqrt.mapInPlace((d) -> {
                if (d == 0) return alpha;
                else return alpha / Math.sqrt(d);
            });

            gradient.elementwiseProductInPlace(sqrt);

            weights.addVectorInPlace(gradient, 1.0);

            // Setup for backtracking, in case necessary

            s.lastDerivative = gradient;
            s.lastLogLikelihood = logLikelihood;

            System.err.println("\tLL: "+logLikelihood);
        }

        return false;
    }

    protected class AdaGradOptimizationState extends OptimizationState {
        ConcatVector lastDerivative = new ConcatVector(0);
        ConcatVector adagradAccumulator = new ConcatVector(0);
        double lastLogLikelihood = Double.NEGATIVE_INFINITY;
    }

    @Override
    protected OptimizationState getFreshOptimizationState(ConcatVector initialWeights) {
        return new AdaGradOptimizationState();
    }
}
