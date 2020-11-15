package edu.stanford.nlp.loglinear.inference;

import com.pholser.junit.quickcheck.ForAll;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import edu.stanford.nlp.loglinear.model.ConcatVector;
import edu.stanford.nlp.loglinear.model.ConcatVectorTable;
import edu.stanford.nlp.loglinear.model.GraphicalModel;
import org.junit.contrib.theories.Theories;
import org.junit.contrib.theories.Theory;
import org.junit.runner.RunWith;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * This is a really tricky thing to test in the quickcheck way, since we basically don't know what we want out of
 * random graphs unless we run the routines that we're trying to test. The trick here is to implement exhaustive
 * factor multiplication, which is normally super intractable but easy to get right, as ground truth.
 *
 * @version 8/11/15
 * @author keenon
 */
@RunWith(Theories.class)
public class CliqueTreeTest {

  @Theory
  public void testCalculateMarginals(@ForAll(sampleSize = 100) @From(GraphicalModelGenerator.class) GraphicalModel model,
                                     @ForAll(sampleSize = 2) @From(WeightsGenerator.class) ConcatVector weights) throws Exception {
    CliqueTree inference = new CliqueTree(model, weights);

    // This is the basic check that inference works when you first construct the model
    checkMarginalsAgainstBruteForce(model, weights, inference);
    // Now we go through several random mutations to the model, and check that everything is still consistent
    Random r = new Random();
    for (int i = 0; i < 10; i++) {
      randomlyMutateGraphicalModel(model, r);
      checkMarginalsAgainstBruteForce(model, weights, inference);
    }
  }

  private void randomlyMutateGraphicalModel(GraphicalModel model, Random r) {
    if (r.nextBoolean() && model.factors.size() > 1) {
      // Remove one factor at random
      model.factors.remove(model.factors.toArray(new GraphicalModel.Factor[model.factors.size()])[r.nextInt(model.factors.size())]);
    } else {
      // Add a simple binary factor, attaching a variable we haven't touched yet, but do observe, to an
      // existing variable. This represents the human observation operation in LENSE
      int maxVar = 0;
      int attachVar = -1;
      int attachVarSize = 0;
      for (GraphicalModel.Factor f : model.factors) {
        for (int j = 0; j < f.neigborIndices.length; j++) {
          int k = f.neigborIndices[j];
          if (k > maxVar) {
            maxVar = k;
          }
          if (r.nextDouble() > 0.3 || attachVar == -1) {
            attachVar = k;
            attachVarSize = f.featuresTable.getDimensions()[j];
          }
        }
      }

      int newVar = maxVar + 1;
      int newVarSize = 1 + r.nextInt(2);

      if (maxVar >= 8) {
        boolean[] seenVariables = new boolean[maxVar + 1];
        for (GraphicalModel.Factor f : model.factors) {
          for (int n : f.neigborIndices) seenVariables[n] = true;
        }
        for (int j = 0; j < seenVariables.length; j++) {
          if (!seenVariables[j]) {
            newVar = j;
            break;
          }
        }

        // This means the model is already too gigantic to be tractable, so we don't add anything here
        if (newVar == maxVar + 1) {
          return;
        }
      }

      if (model.getVariableMetaDataByReference(newVar).containsKey(CliqueTree.VARIABLE_OBSERVED_VALUE)) {
        int assignment = Integer.parseInt(model.getVariableMetaDataByReference(newVar).get(CliqueTree.VARIABLE_OBSERVED_VALUE));
        if (assignment >= newVarSize) {
          newVarSize = assignment + 1;
        }
      }

      GraphicalModel.Factor binary = model.addFactor(new int[]{newVar, attachVar}, new int[]{newVarSize, attachVarSize}, (assignment) -> {
        ConcatVector v = new ConcatVector(CONCAT_VEC_COMPONENTS);
        for (int j = 0; j < v.getNumberOfComponents(); j++) {
          if (r.nextBoolean()) {
            v.setSparseComponent(j, r.nextInt(CONCAT_VEC_COMPONENT_LENGTH), r.nextDouble());
          } else {
            double[] d = new double[CONCAT_VEC_COMPONENT_LENGTH];
            for (int k = 0; k < d.length; k++) {
              d[k] = r.nextDouble();
            }
            v.setDenseComponent(j, d);
          }
        }
        return v;
      });

      // "Cook" the randomly generated feature vector thunks, so they don't change as we run the system

      for (int[] assignment : binary.featuresTable) {
        ConcatVector randomlyGenerated = binary.featuresTable.getAssignmentValue(assignment).get();
        binary.featuresTable.setAssignmentValue(assignment, () -> randomlyGenerated);
      }
    }
  }

  private void checkMarginalsAgainstBruteForce(GraphicalModel model, ConcatVector weights, CliqueTree inference) {
    CliqueTree.MarginalResult result = inference.calculateMarginals();

    double[][] marginals = result.marginals;
    Set<TableFactor> tableFactors = model.factors.stream().map(factor -> new TableFactor(weights, factor)).collect(Collectors.toSet());
    assert (tableFactors.size() == model.factors.size());

    // this is the super slow but obviously correct way to get global marginals

    TableFactor bruteForce = null;
    for (TableFactor factor : tableFactors) {
      if (bruteForce == null) bruteForce = factor;
      else bruteForce = bruteForce.multiply(factor);
    }

    if (bruteForce != null) {

      // observe out all variables that have been registered

      TableFactor observed = bruteForce;
      for (int i = 0; i < bruteForce.neighborIndices.length; i++) {
        int n = bruteForce.neighborIndices[i];
        if (model.getVariableMetaDataByReference(n).containsKey(CliqueTree.VARIABLE_OBSERVED_VALUE)) {
          int value = Integer.parseInt(model.getVariableMetaDataByReference(n).get(CliqueTree.VARIABLE_OBSERVED_VALUE));
          // Check that the marginals reflect the observation
          for (int j = 0; j < marginals[n].length; j++) {
            assertEquals(j == value ? 1.0 : 0.0, marginals[n][j], 1.0e-9);
          }
          if (observed.neighborIndices.length > 1) {
            observed = observed.observe(n, value);
          }
          // If we've observed everything, then just quit
          else return;
        }
      }
      bruteForce = observed;

      // Spot check each of the marginals in the brute force calculation
      double[][] bruteMarginals = bruteForce.getSummedMarginals();
      int index = 0;
      for (int i : bruteForce.neighborIndices) {
        boolean isEqual = true;

        double[] brute = bruteMarginals[index];
        index++;
        assert (brute != null);
        assert (marginals[i] != null);
        for (int j = 0; j < brute.length; j++) {
          if (Double.isNaN(brute[j])) {
            isEqual = false;
            break;
          }
          if (Math.abs(brute[j] - marginals[i][j]) > 3.0e-2) {
            isEqual = false;
            break;
          }
        }
        if (!isEqual) {
          System.err.println("Arrays not equal! Variable " + i);
          System.err.println("\tGold: " + Arrays.toString(brute));
          System.err.println("\tResult: " + Arrays.toString(marginals[i]));
        }
        assertArrayEquals(brute, marginals[i], 3.0e-2);
      }

      // Spot check the partition function
      double goldPartitionFunction = bruteForce.valueSum();

      // Correct to within 3%
      assertEquals(goldPartitionFunction, result.partitionFunction, goldPartitionFunction * 3.0e-2);

      // Check the joint marginals
      marginals:
      for (GraphicalModel.Factor f : model.factors) {
        assertTrue(result.jointMarginals.containsKey(f));

        TableFactor bruteForceJointMarginal = bruteForce;
        outer:
        for (int n : bruteForce.neighborIndices) {
          for (int i : f.neigborIndices)
            if (i == n) {
              continue outer;
            }
          if (bruteForceJointMarginal.neighborIndices.length > 1) {
            bruteForceJointMarginal = bruteForceJointMarginal.sumOut(n);
          } else {
            int[] fixedAssignment = new int[f.neigborIndices.length];
            for (int i = 0; i < fixedAssignment.length; i++) {
              fixedAssignment[i] = Integer.parseInt(model.getVariableMetaDataByReference(f.neigborIndices[i]).get(CliqueTree.VARIABLE_OBSERVED_VALUE));
            }
            for (int[] assn : result.jointMarginals.get(f)) {
              if (Arrays.equals(assn, fixedAssignment)) {
                assertEquals(1.0, result.jointMarginals.get(f).getAssignmentValue(assn), 1.0e-7);
              } else {
                if (result.jointMarginals.get(f).getAssignmentValue(assn) != 0) {
                  TableFactor j = result.jointMarginals.get(f);
                  for (int[] assignment : j) {
                    System.err.println(Arrays.toString(assignment) + ": " + j.getAssignmentValue(assignment));
                  }
                }
                assertEquals(0.0, result.jointMarginals.get(f).getAssignmentValue(assn), 1.0e-7);
              }
            }
            continue marginals;
          }
        }

        // Find the correspondence between the brute force joint marginal, which may be missing variables
        // because they were observed out of the table, and the output joint marginals, which are always an exact
        // match for the original factor

        int[] backPointers = new int[f.neigborIndices.length];
        int[] observedValue = new int[f.neigborIndices.length];
        for (int i = 0; i < backPointers.length; i++) {
          if (model.getVariableMetaDataByReference(f.neigborIndices[i]).containsKey(CliqueTree.VARIABLE_OBSERVED_VALUE)) {
            observedValue[i] = Integer.parseInt(model.getVariableMetaDataByReference(f.neigborIndices[i]).get(CliqueTree.VARIABLE_OBSERVED_VALUE));
            backPointers[i] = -1;
          } else {
            observedValue[i] = -1;
            backPointers[i] = -1;
            for (int j = 0; j < bruteForceJointMarginal.neighborIndices.length; j++) {
              if (bruteForceJointMarginal.neighborIndices[j] == f.neigborIndices[i]) {
                backPointers[i] = j;
              }
            }
            assert (backPointers[i] != -1);
          }
        }

        double sum = bruteForceJointMarginal.valueSum();
        if (sum == 0.0) sum = 1;

        outer:
        for (int[] assignment : result.jointMarginals.get(f)) {
          int[] bruteForceMarginalAssignment = new int[bruteForceJointMarginal.neighborIndices.length];
          for (int i = 0; i < assignment.length; i++) {
            if (backPointers[i] != -1) {
              bruteForceMarginalAssignment[backPointers[i]] = assignment[i];
            }
            // Make sure all assignments that don't square with observations get 0 weight
            else {
              assert (observedValue[i] != -1);
              if (assignment[i] != observedValue[i]) {
                if (result.jointMarginals.get(f).getAssignmentValue(assignment) != 0) {
                  System.err.println("Joint marginals: " + Arrays.toString(result.jointMarginals.get(f).neighborIndices));
                  System.err.println("Assignment: " + Arrays.toString(assignment));
                  System.err.println("Observed Value: " + Arrays.toString(observedValue));
                  for (int[] assn : result.jointMarginals.get(f)) {
                    System.err.println("\t" + Arrays.toString(assn) + ":" + result.jointMarginals.get(f).getAssignmentValue(assn));
                  }
                }
                assertEquals(0.0, result.jointMarginals.get(f).getAssignmentValue(assignment), 1.0e-7);
                continue outer;
              }
            }
          }

          assertEquals(bruteForceJointMarginal.getAssignmentValue(bruteForceMarginalAssignment) / sum, result.jointMarginals.get(f).getAssignmentValue(assignment), 1.0e-3);
        }
      }
    } else {
      for (double[] marginal : marginals) {
        for (double d : marginal) {
          assertEquals(1.0 / marginal.length, d, 3.0e-2);
        }
      }
    }
  }

  @Theory
  public void testCalculateMap(@ForAll(sampleSize = 100) @From(GraphicalModelGenerator.class) GraphicalModel model,
                               @ForAll(sampleSize = 2) @From(WeightsGenerator.class) ConcatVector weights) throws Exception {
    if (model.factors.size() == 0) return;
    CliqueTree inference = new CliqueTree(model, weights);
    // This is the basic check that inference works when you first construct the model
    checkMAPAgainstBruteForce(model, weights, inference);
    // Now we go through several random mutations to the model, and check that everything is still consistent
    Random r = new Random();
    for (int i = 0; i < 10; i++) {
      randomlyMutateGraphicalModel(model, r);
      checkMAPAgainstBruteForce(model, weights, inference);
    }
  }

  public void checkMAPAgainstBruteForce(GraphicalModel model, ConcatVector weights, CliqueTree inference) {
    int[] map = inference.calculateMAP();

    Set<TableFactor> tableFactors = model.factors.stream().map(factor -> new TableFactor(weights, factor)).collect(Collectors.toSet());

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
        else return;
      }
    }
    bruteForce = observed;

    int largestVariableNum = 0;
    for (GraphicalModel.Factor f : model.factors) {
      for (int i : f.neigborIndices) if (i > largestVariableNum) largestVariableNum = i;
    }

    // this is presented in true order, where 0 corresponds to var 0
    int[] mapValueAssignment = new int[largestVariableNum + 1];
    // this is kept in the order that the factor presents to us
    int[] highestValueAssignment = new int[bruteForce.neighborIndices.length];
    for (int[] assignment : bruteForce) {
      if (bruteForce.getAssignmentValue(assignment) > bruteForce.getAssignmentValue(highestValueAssignment)) {
        highestValueAssignment = assignment;
        for (int i = 0; i < assignment.length; i++) {
          mapValueAssignment[bruteForce.neighborIndices[i]] = assignment[i];
        }
      }
    }

    int[] forcedAssignments = new int[largestVariableNum + 1];
    for (int i = 0; i < mapValueAssignment.length; i++) {
      if (model.getVariableMetaDataByReference(i).containsKey(CliqueTree.VARIABLE_OBSERVED_VALUE)) {
        mapValueAssignment[i] = Integer.parseInt(model.getVariableMetaDataByReference(i).get(CliqueTree.VARIABLE_OBSERVED_VALUE));
        forcedAssignments[i] = mapValueAssignment[i];
      }
    }

    if (!Arrays.equals(mapValueAssignment, map)) {
      System.err.println("---");
      System.err.println("Relevant variables: " + Arrays.toString(bruteForce.neighborIndices));
      System.err.println("Var Sizes: " + Arrays.toString(bruteForce.getDimensions()));
      System.err.println("MAP: " + Arrays.toString(map));
      System.err.println("Brute force map: " + Arrays.toString(mapValueAssignment));
      System.err.println("Forced assignments: " + Arrays.toString(forcedAssignments));
    }

    for (int i : bruteForce.neighborIndices) {
      // Only check defined variables
      assertEquals(mapValueAssignment[i], map[i]);
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  //
  // A copy of these generators exists in GradientSourceTest in the learning module. If any bug fixes are made here,
  // remember to update that code as well by copy-paste.
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

      if (sourceOfRandomness.nextBoolean()) {
        generateCliques(variableSizes, new ArrayList<>(), new HashSet<>(), model, sourceOfRandomness);
      }

      // Or generate a linear chain CRF, because our random BFS doesn't generate these very often, and they're very
      // common in practice, so worth testing densely

      else {
        for (int i = 0; i < variableSizes.length; i++) {

          // Add unary factor

          GraphicalModel.Factor unary = model.addFactor(new int[]{i}, new int[]{variableSizes[i]}, (assignment) -> {
            ConcatVector features = new ConcatVector(CONCAT_VEC_COMPONENTS);
            for (int j = 0; j < CONCAT_VEC_COMPONENTS; j++) {
              if (sourceOfRandomness.nextBoolean()) {
                features.setSparseComponent(j, sourceOfRandomness.nextInt(CONCAT_VEC_COMPONENT_LENGTH), sourceOfRandomness.nextDouble());
              } else {
                double[] dense = new double[sourceOfRandomness.nextInt(CONCAT_VEC_COMPONENT_LENGTH)];
                for (int k = 0; k < dense.length; k++) {
                  dense[k] = sourceOfRandomness.nextDouble();
                }
                features.setDenseComponent(j, dense);
              }
            }
            return features;
          });

          // "Cook" the randomly generated feature vector thunks, so they don't change as we run the system

          for (int[] assignment : unary.featuresTable) {
            ConcatVector randomlyGenerated = unary.featuresTable.getAssignmentValue(assignment).get();
            unary.featuresTable.setAssignmentValue(assignment, () -> randomlyGenerated);
          }

          // Add binary factor

          if (i < variableSizes.length - 1) {
            GraphicalModel.Factor binary = model.addFactor(new int[]{i, i + 1}, new int[]{variableSizes[i], variableSizes[i + 1]}, (assignment) -> {
              ConcatVector features = new ConcatVector(CONCAT_VEC_COMPONENTS);
              for (int j = 0; j < CONCAT_VEC_COMPONENTS; j++) {
                if (sourceOfRandomness.nextBoolean()) {
                  features.setSparseComponent(j, sourceOfRandomness.nextInt(CONCAT_VEC_COMPONENT_LENGTH), sourceOfRandomness.nextDouble());
                } else {
                  double[] dense = new double[sourceOfRandomness.nextInt(CONCAT_VEC_COMPONENT_LENGTH)];
                  for (int k = 0; k < dense.length; k++) {
                    dense[k] = sourceOfRandomness.nextDouble();
                  }
                  features.setDenseComponent(j, dense);
                }
              }
              return features;
            });

            // "Cook" the randomly generated feature vector thunks, so they don't change as we run the system

            for (int[] assignment : binary.featuresTable) {
              ConcatVector randomlyGenerated = binary.featuresTable.getAssignmentValue(assignment).get();
              binary.featuresTable.setAssignmentValue(assignment, () -> randomlyGenerated);
            }
          }
        }
      }

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
      alreadyRepresented.addAll(startSet);
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
            v.setSparseComponent(x, randomness.nextInt(32), randomness.nextDouble());
          } else {
            double[] val = new double[randomness.nextInt(12)];
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