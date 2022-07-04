package edu.stanford.nlp.loglinear.learning;

import edu.stanford.nlp.loglinear.model.ConcatVector;
import edu.stanford.nlp.loglinear.model.GraphicalModel;
import com.pholser.junit.quickcheck.ForAll;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.generator.InRange;
import org.junit.contrib.theories.DataPoint;
import org.junit.contrib.theories.Theories;
import org.junit.contrib.theories.Theory;
import org.junit.runner.RunWith;

import java.util.Random;

import static org.junit.Assert.*;

/**
 * This does its best to quick check our optimizers. The strategy here is to generate convex functions that are solvable
 * in closed form, and then test that our optimizer is able to achieve a nearly optimal solution at convergence.
 * <p>
 * Created on 8/26/15.
 * @author keenon
 */
@RunWith(Theories.class)
public class OptimizerTests {

  @SuppressWarnings("unused") // This is somehow used in the theories stuff, though I don't quite understand...
  @DataPoint
  public static AbstractBatchOptimizer backtrackingAdaGrad = new BacktrackingAdaGradOptimizer();

  @SuppressWarnings("DefaultAnnotationParam")
  @Theory
  public void testOptimizeLogLikelihood(AbstractBatchOptimizer optimizer,
                                        @ForAll(sampleSize = 5) @From(LogLikelihoodFunctionTest.GraphicalModelDatasetGenerator.class) GraphicalModel[] dataset,
                                        @ForAll(sampleSize = 2) @From(LogLikelihoodFunctionTest.WeightsGenerator.class) ConcatVector initialWeights,
                                        @ForAll(sampleSize = 2) @InRange(minDouble = 0.0, maxDouble = 5.0) double l2regularization) {
    AbstractDifferentiableFunction<GraphicalModel> ll = new LogLikelihoodDifferentiableFunction();
    ConcatVector finalWeights = optimizer.optimize(dataset, ll, initialWeights, l2regularization, 1.0e-5, true);
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
          dense[k] = (r.nextDouble() - 0.5) * 1.0e-3;
        }
        randomDirection.setDenseComponent(j, dense);
      }

      ConcatVector randomPerturbation = finalWeights.deepClone();
      randomPerturbation.addVectorInPlace(randomDirection, 1.0);

      double randomPerturbedLogLikelihood = getValueSum(dataset, randomPerturbation, ll, l2regularization);

      // Check that we're within a very small margin of error (around 3 decimal places) of the randomly
      // discovered value

      double allowedDeviation = randomPerturbedLogLikelihood - (1.0e-3 * Math.max(1.0, Math.abs(logLikelihood)));
      if (logLikelihood < allowedDeviation) {
        System.err.println("Thought optimal point was: " + logLikelihood);
        System.err.println("Discovered better point: " + randomPerturbedLogLikelihood);
      }

      assertTrue(logLikelihood >= allowedDeviation);
    }
  }

  /*
  @Theory
  public void testOptimizeLogLikelihoodWithConstraints(AbstractBatchOptimizer optimizer,
                                                       @ForAll(sampleSize = 5) @From(LogLikelihoodFunctionTest.GraphicalModelDatasetGenerator.class) GraphicalModel[] dataset,
                                                       @ForAll(sampleSize = 2) @From(LogLikelihoodFunctionTest.WeightsGenerator.class) ConcatVector initialWeights,
                                                       @ForAll(sampleSize = 2) @InRange(minDouble = 0.0, maxDouble = 5.0) double l2regularization) throws Exception {
    Random r = new Random(42);

    int constraintComponent = r.nextInt(initialWeights.getNumberOfComponents());
    double constraintValue = r.nextDouble();

    if (r.nextBoolean()) {
      optimizer.addSparseConstraint(constraintComponent, 0, constraintValue);
    } else {
      optimizer.addDenseConstraint(constraintComponent, new double[]{constraintValue});
    }

    // Put in some constraints

    AbstractDifferentiableFunction<GraphicalModel> ll = new LogLikelihoodDifferentiableFunction();
    ConcatVector finalWeights = optimizer.optimize(dataset, ll, initialWeights, l2regularization, 1.0e-9, false);
    System.err.println("Finished optimizing");

    assertEquals(constraintValue, finalWeights.getValueAt(constraintComponent, 0), 1.0e-9);

    double logLikelihood = getValueSum(dataset, finalWeights, ll, l2regularization);

    // Check in a whole bunch of random directions really nearby that there is no nearby point with a higher log
    // likelihood
    for (int i = 0; i < 1000; i++) {
      int size = finalWeights.getNumberOfComponents();
      ConcatVector randomDirection = new ConcatVector(size);
      for (int j = 0; j < size; j++) {
        if (j == constraintComponent) continue;
        double[] dense = new double[finalWeights.isComponentSparse(j) ? finalWeights.getSparseIndex(j) + 1 : finalWeights.getDenseComponent(j).length];
        for (int k = 0; k < dense.length; k++) {
          dense[k] = (r.nextDouble() - 0.5) * 1.0e-3;
        }
        randomDirection.setDenseComponent(j, dense);
      }

      ConcatVector randomPerturbation = finalWeights.deepClone();
      randomPerturbation.addVectorInPlace(randomDirection, 1.0);

      double randomPerturbedLogLikelihood = getValueSum(dataset, randomPerturbation, ll, l2regularization);

      // Check that we're within a very small margin of error (around 3 decimal places) of the randomly
      // discovered value

      if (logLikelihood < randomPerturbedLogLikelihood - (1.0e-3 * Math.max(1.0, Math.abs(logLikelihood)))) {
        System.err.println("Thought optimal point was: " + logLikelihood);
        System.err.println("Discovered better point: " + randomPerturbedLogLikelihood);
      }

      assertTrue(logLikelihood >= randomPerturbedLogLikelihood - (1.0e-3 * Math.max(1.0, Math.abs(logLikelihood))));
    }
  }
  */

  private static <T> double getValueSum(T[] dataset, ConcatVector weights, AbstractDifferentiableFunction<T> fn, double l2regularization) {
    double value = 0.0;
    for (T t : dataset) {
      value += fn.getSummaryForInstance(t, weights, new ConcatVector(0));
    }
    return (value / dataset.length) - (weights.dotProduct(weights) * l2regularization);
  }

}