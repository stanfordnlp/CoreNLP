package edu.stanford.nlp.loglinear.learning;

import com.pholser.junit.quickcheck.ForAll;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.generator.InRange;
import edu.stanford.nlp.loglinear.model.ConcatVector;
import edu.stanford.nlp.loglinear.model.GraphicalModel;
import org.junit.contrib.theories.DataPoint;
import org.junit.contrib.theories.Theories;
import org.junit.contrib.theories.Theory;
import org.junit.runner.RunWith;

import java.util.Random;

import static org.junit.Assert.assertTrue;

/**
 * Created by keenon on 8/26/15.
 *
 * This does its best to Quickcheck our optimizers. The strategy here is to generate convex functions that are solvable
 * in closed form, and then test that our optimizer is able to achieve a nearly optimal solution at convergence.
 */
@RunWith(Theories.class)
public class OptimizerTests {
    @DataPoint
    public static AbstractBatchOptimizer backtrackingAdaGrad = new BacktrackingAdaGradOptimizer();

    @Theory
    public void testOptimizeLogLikelihood(AbstractBatchOptimizer optimizer,
                                          @ForAll(sampleSize = 50) @From(LogLikelihoodFunctionTest.GraphicalModelDatasetGenerator.class) GraphicalModel[] dataset,
                                          @ForAll(sampleSize = 2) @From(LogLikelihoodFunctionTest.WeightsGenerator.class) ConcatVector initialWeights,
                                          @ForAll(sampleSize = 4) @InRange(minDouble = 0.0, maxDouble = 5.0) double l2regularization) throws Exception {
        AbstractDifferentiableFunction<GraphicalModel> ll = new LogLikelihoodFunction();
        ConcatVector finalWeights = optimizer.optimize(dataset, ll, initialWeights, l2regularization);
        System.err.println("Finished optimizing");

        double logLikelihood = getValueSum(dataset, finalWeights, ll, l2regularization);

        // Check in a whole bunch of random directions really nearby that there is no nearby point with a higher log
        // likelihood
        Random r = new Random(42);
        for (int i = 0; i < 1000; i++) {
            int size = finalWeights.getNumberOfComponents();
            ConcatVector randomDirection = new ConcatVector(size);
            for (int j = 0; j < size; j++) {
                double[] dense = new double[finalWeights.isComponentSparse(j) ? finalWeights.getSparseIndex(j) + 1 : finalWeights.getDenseComponent(j).length];
                for (int k = 0; k < dense.length; k++) {
                    dense[k] = (r.nextDouble()-0.5)*1.0e-3;
                }
                randomDirection.setDenseComponent(j, dense);
            }

            ConcatVector randomPerturbation = finalWeights.deepClone();
            randomPerturbation.addVectorInPlace(randomDirection, 1.0);

            double randomPerturbedLogLikelihood = getValueSum(dataset, randomPerturbation, ll, l2regularization);

            // Check that we're within a very small margin of error (around 7 decimal places) of the randomly
            // discovered value

            if (logLikelihood < randomPerturbedLogLikelihood - Math.abs(1.0e-7 * logLikelihood)) {
                System.err.println("Thought optimal point was: "+logLikelihood);
                System.err.println("Discovered better point: "+randomPerturbedLogLikelihood);
            }

            assertTrue(logLikelihood >= randomPerturbedLogLikelihood - Math.abs(1.0e-7 * logLikelihood));
        }
    }

    private <T> double getValueSum(T[] dataset, ConcatVector weights, AbstractDifferentiableFunction<T> fn, double l2regularization) {
        double value = 0.0;
        for (T t : dataset) {
            value += fn.getSummaryForInstance(t, weights).value;
        }
        return (value / dataset.length) - (weights.dotProduct(weights) * l2regularization);
    }
}