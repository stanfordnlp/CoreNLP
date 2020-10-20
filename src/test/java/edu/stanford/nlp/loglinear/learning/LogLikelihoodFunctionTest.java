package edu.stanford.nlp.loglinear.learning;

import com.pholser.junit.quickcheck.ForAll;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import edu.stanford.nlp.loglinear.inference.CliqueTree;
import edu.stanford.nlp.loglinear.inference.TableFactor;
import edu.stanford.nlp.loglinear.model.ConcatVector;
import edu.stanford.nlp.loglinear.model.ConcatVectorTable;
import edu.stanford.nlp.loglinear.model.GraphicalModel;
import org.junit.contrib.theories.Theories;
import org.junit.contrib.theories.Theory;
import org.junit.runner.RunWith;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * Created on 8/24/15.
 * @author keenon
 * <p>
 * Uses the definition of the derivative to verify that the calculated gradients are approximately correct.
 */
@RunWith(Theories.class)
public class LogLikelihoodFunctionTest {
  @Theory
  public void testGetSummaryForInstance(@ForAll(sampleSize = 50) @From(GraphicalModelDatasetGenerator.class) GraphicalModel[] dataset,
                                        @ForAll(sampleSize = 2) @From(WeightsGenerator.class) ConcatVector weights) throws Exception {
    LogLikelihoodDifferentiableFunction fn = new LogLikelihoodDifferentiableFunction();
    for (GraphicalModel model : dataset) {
      double goldLogLikelihood = logLikelihood(model, weights);
      ConcatVector goldGradient = definitionOfDerivative(model, weights);

      ConcatVector gradient = new ConcatVector(0);
      double logLikelihood = fn.getSummaryForInstance(model, weights, gradient);

      assertEquals(goldLogLikelihood, logLikelihood, Math.max(1.0e-3, goldLogLikelihood * 1.0e-2));

      // Our check for gradient similarity involves distance between endpoints of vectors, instead of elementwise
      // similarity, b/c it can be controlled as a percentage
      ConcatVector difference = goldGradient.deepClone();
      difference.addVectorInPlace(gradient, -1);

      double distance = Math.sqrt(difference.dotProduct(difference));

      // The tolerance here is pretty large, since the gold gradient is computed approximately
      // 5% still tells us whether everything is working or not though
      if (distance > 5.0e-2) {
        System.err.println("Definitional and calculated gradient differ!");
        System.err.println("Definition approx: " + goldGradient);
        System.err.println("Calculated: " + gradient);
      }

      assertEquals(0.0, distance, 5.0e-2);
    }
  }

  /**
   * The slowest, but obviously correct way to get log likelihood. We've already tested the partition function in
   * the CliqueTreeTest, but in the interest of making things as different as possible to catch any lurking bugs or
   * numerical issues, we use the brute force approach here.
   *
   * @param model   the model to get the log-likelihood of, assumes labels for assignments
   * @param weights the weights to get the log-likelihood at
   * @return the log-likelihood
   */
  private double logLikelihood(GraphicalModel model, ConcatVector weights) {
    Set<TableFactor> tableFactors = model.factors.stream().map(factor -> new TableFactor(weights, factor)).collect(Collectors.toSet());
    assert (tableFactors.size() == model.factors.size());

    // this is the super slow but obviously correct way to get global marginals

    TableFactor bruteForce = null;
    for (TableFactor factor : tableFactors) {
      if (bruteForce == null) bruteForce = factor;
      else bruteForce = bruteForce.multiply(factor);
    }
    assert (bruteForce != null);

    // observe out all variables that have been registered

    TableFactor observed = bruteForce;
    for (int n : bruteForce.neighborIndices) {
      if (model.getVariableMetaDataByReference(n).containsKey(CliqueTree.VARIABLE_OBSERVED_VALUE)) {
        int value = Integer.parseInt(model.getVariableMetaDataByReference(n).get(CliqueTree.VARIABLE_OBSERVED_VALUE));
        if (observed.neighborIndices.length > 1) {
          observed = observed.observe(n, value);
        }
        // If we've observed everything, then just quit
        else return 0.0;
      }
    }
    bruteForce = observed;

    // Now we can get a partition function

    double partitionFunction = bruteForce.valueSum();

    // For now, we'll assume that all the variables are given for training. EM is another problem altogether

    int[] assignment = new int[bruteForce.neighborIndices.length];
    for (int i = 0; i < assignment.length; i++) {
      assert (!model.getVariableMetaDataByReference(bruteForce.neighborIndices[i]).containsKey(CliqueTree.VARIABLE_OBSERVED_VALUE));
      assignment[i] = Integer.parseInt(model.getVariableMetaDataByReference(bruteForce.neighborIndices[i]).get(LogLikelihoodDifferentiableFunction.VARIABLE_TRAINING_VALUE));
    }

    if (bruteForce.getAssignmentValue(assignment) == 0 || partitionFunction == 0) {
      return Double.NEGATIVE_INFINITY;
    }
    return Math.log(bruteForce.getAssignmentValue(assignment)) - Math.log(partitionFunction);
  }

  /**
   * Slowest possible way to calculate a derivative for a model: exhaustive definitional calculation, using the super
   * slow logLikelihood function from this test suite.
   *
   * @param model   the model the get the derivative for
   * @param weights the weights to get the derivative at
   * @return the derivative of the log likelihood with respect to the weights
   */
  private ConcatVector definitionOfDerivative(GraphicalModel model, ConcatVector weights) {
    double epsilon = 1.0e-7;

    ConcatVector goldGradient = new ConcatVector(CONCAT_VEC_COMPONENTS);
    for (int i = 0; i < CONCAT_VEC_COMPONENTS; i++) {
      double[] component = new double[CONCAT_VEC_COMPONENT_LENGTH];
      for (int j = 0; j < CONCAT_VEC_COMPONENT_LENGTH; j++) {
        // Create a unit vector pointing in the direction of this element of this component
        ConcatVector unitVectorIJ = new ConcatVector(CONCAT_VEC_COMPONENTS);
        unitVectorIJ.setSparseComponent(i, j, 1.0);

        // Create a +eps weight vector
        ConcatVector weightsPlusEpsilon = weights.deepClone();
        weightsPlusEpsilon.addVectorInPlace(unitVectorIJ, epsilon);

        // Create a -eps weight vector
        ConcatVector weightsMinusEpsilon = weights.deepClone();
        weightsMinusEpsilon.addVectorInPlace(unitVectorIJ, -epsilon);

        // Use the definition (f(x+eps) - f(x-eps))/(2*eps)
        component[j] = (logLikelihood(model, weightsPlusEpsilon) - logLikelihood(model, weightsMinusEpsilon)) / (2 * epsilon);
        // If we encounter an impossible assignment, logLikelihood will return negative infinity, which will
        // screw with the definitional calculation
        if (Double.isNaN(component[j])) component[j] = 0.0;
      }
      goldGradient.setDenseComponent(i, component);
    }

    return goldGradient;
  }

  public static class GraphicalModelDatasetGenerator extends Generator<GraphicalModel[]> {
    GraphicalModelGenerator modelGenerator = new GraphicalModelGenerator(GraphicalModel.class);

    public GraphicalModelDatasetGenerator(Class<GraphicalModel[]> type) {
      super(type);
    }

    @Override
    public GraphicalModel[] generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus) {
      GraphicalModel[] dataset = new GraphicalModel[sourceOfRandomness.nextInt(1, 10)];
      for (int i = 0; i < dataset.length; i++) {
        dataset[i] = modelGenerator.generate(sourceOfRandomness, generationStatus);
        for (GraphicalModel.Factor f : dataset[i].factors) {
          for (int j = 0; j < f.neigborIndices.length; j++) {
            int n = f.neigborIndices[j];
            int dim = f.featuresTable.getDimensions()[j];
            dataset[i].getVariableMetaDataByReference(n).put(LogLikelihoodDifferentiableFunction.VARIABLE_TRAINING_VALUE, "" + sourceOfRandomness.nextInt(dim));
          }
        }
      }
      return dataset;
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  //
  // These generators COPIED DIRECTLY FROM CliqueTreeTest in the inference module.
  //
  /////////////////////////////////////////////////////////////////////////////

  public static final int CONCAT_VEC_COMPONENTS = 2;
  public static final int CONCAT_VEC_COMPONENT_LENGTH = 3;

  public static class WeightsGenerator extends Generator<ConcatVector> {
    public WeightsGenerator(Class<ConcatVector> type) {
      super(type);
    }

    @Override
    public ConcatVector generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus) {
      ConcatVector v = new ConcatVector(CONCAT_VEC_COMPONENTS);
      for (int x = 0; x < CONCAT_VEC_COMPONENTS; x++) {
        if (sourceOfRandomness.nextBoolean()) {
          v.setSparseComponent(x, sourceOfRandomness.nextInt(CONCAT_VEC_COMPONENT_LENGTH), sourceOfRandomness.nextDouble());
        } else {
          double[] val = new double[sourceOfRandomness.nextInt(CONCAT_VEC_COMPONENT_LENGTH)];
          for (int y = 0; y < val.length; y++) {
            val[y] = sourceOfRandomness.nextDouble();
          }
          v.setDenseComponent(x, val);
        }
      }
      return v;
    }
  }

  public static class GraphicalModelGenerator extends Generator<GraphicalModel> {
    public GraphicalModelGenerator(Class<GraphicalModel> type) {
      super(type);
    }

    private Map<String, String> generateMetaData(SourceOfRandomness sourceOfRandomness, Map<String, String> metaData) {
      int numPairs = sourceOfRandomness.nextInt(9);
      for (int i = 0; i < numPairs; i++) {
        int key = sourceOfRandomness.nextInt();
        int value = sourceOfRandomness.nextInt();
        metaData.put("key:" + key, "value:" + value);
      }
      return metaData;
    }

    @Override
    public GraphicalModel generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus) {
      GraphicalModel model = new GraphicalModel();

      // Create the variables and factors. These are deliberately tiny so that the brute force approach is tractable

      int[] variableSizes = new int[8];
      for (int i = 0; i < variableSizes.length; i++) {
        variableSizes[i] = sourceOfRandomness.nextInt(1, 3);
      }

      // Traverse in a randomized BFS to ensure the generated graph is a tree

      generateCliques(variableSizes, new ArrayList<>(), new HashSet<>(), model, sourceOfRandomness);

      // Add metadata to the variables, factors, and model

      generateMetaData(sourceOfRandomness, model.getModelMetaDataByReference());
      for (int i = 0; i < 20; i++) {
        generateMetaData(sourceOfRandomness, model.getVariableMetaDataByReference(i));
      }
      for (GraphicalModel.Factor factor : model.factors) {
        generateMetaData(sourceOfRandomness, factor.getMetaDataByReference());
      }

      // Observe a few of the variables

      for (GraphicalModel.Factor f : model.factors) {
        for (int i = 0; i < f.neigborIndices.length; i++) {
          if (sourceOfRandomness.nextDouble() > 0.8) {
            int obs = sourceOfRandomness.nextInt(f.featuresTable.getDimensions()[i]);
            model.getVariableMetaDataByReference(f.neigborIndices[i]).put(CliqueTree.VARIABLE_OBSERVED_VALUE, "" + obs);
          }
        }
      }

      return model;
    }

    private void generateCliques(int[] variableSizes,
                                 List<Integer> startSet,
                                 Set<Integer> alreadyRepresented,
                                 GraphicalModel model,
                                 SourceOfRandomness randomness) {
      if (alreadyRepresented.size() == variableSizes.length) return;

      // Generate the clique variable set

      List<Integer> cliqueContents = new ArrayList<>();
      cliqueContents.addAll(startSet);
      while (true) {
        if (alreadyRepresented.size() == variableSizes.length) break;
        if (cliqueContents.size() == 0 || randomness.nextDouble(0, 1) < 0.7) {
          int gen;
          do {
            gen = randomness.nextInt(variableSizes.length);
          } while (alreadyRepresented.contains(gen));
          alreadyRepresented.add(gen);
          cliqueContents.add(gen);
        } else break;
      }

      // Create the actual table

      int[] neighbors = new int[cliqueContents.size()];
      int[] neighborSizes = new int[neighbors.length];
      for (int j = 0; j < neighbors.length; j++) {
        neighbors[j] = cliqueContents.get(j);
        neighborSizes[j] = variableSizes[neighbors[j]];
      }
      ConcatVectorTable table = new ConcatVectorTable(neighborSizes);
      for (int[] assignment : table) {
        // Generate a vector
        ConcatVector v = new ConcatVector(CONCAT_VEC_COMPONENTS);
        for (int x = 0; x < CONCAT_VEC_COMPONENTS; x++) {
          if (randomness.nextBoolean()) {
            v.setSparseComponent(x, randomness.nextInt(CONCAT_VEC_COMPONENT_LENGTH), randomness.nextDouble());
          } else {
            double[] val = new double[randomness.nextInt(CONCAT_VEC_COMPONENT_LENGTH)];
            for (int y = 0; y < val.length; y++) {
              val[y] = randomness.nextDouble();
            }
            v.setDenseComponent(x, val);
          }
        }
        // set vec in table
        table.setAssignmentValue(assignment, () -> v);
      }
      model.addFactor(table, neighbors);

      // Pick the number of children

      List<Integer> availableVariables = new ArrayList<>();
      availableVariables.addAll(cliqueContents);
      availableVariables.removeAll(startSet);

      int numChildren = randomness.nextInt(0, availableVariables.size());
      if (numChildren == 0) return;

      List<List<Integer>> children = new ArrayList<>();
      for (int i = 0; i < numChildren; i++) {
        children.add(new ArrayList<>());
      }

      // divide up the shared variables across the children

      int cursor = 0;
      while (true) {
        if (availableVariables.size() == 0) break;
        if (children.get(cursor).size() == 0 || randomness.nextBoolean()) {
          int gen = randomness.nextInt(availableVariables.size());
          children.get(cursor).add(availableVariables.get(gen));
          availableVariables.remove(availableVariables.get(gen));
        } else break;

        cursor = (cursor + 1) % numChildren;
      }

      for (List<Integer> shared1 : children) {
        for (int i : shared1) {
          for (List<Integer> shared2 : children) {
            assert (shared1 == shared2 || !shared2.contains(i));
          }
        }
      }

      for (List<Integer> shared : children) {
        if (shared.size() > 0) generateCliques(variableSizes, shared, alreadyRepresented, model, randomness);
      }
    }
  }
}