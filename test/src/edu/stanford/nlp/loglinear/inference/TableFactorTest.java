package edu.stanford.nlp.loglinear.inference;

import com.pholser.junit.quickcheck.ForAll;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.InRange;
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
import static org.junit.Assume.assumeTrue;

/**
 * Created on 8/12/15.
 * @author keenon
 * <p>
 * Tries to quickcheck our factor functions, as well as unit test for documentation and simple verification.
 */
@RunWith(Theories.class)
public class TableFactorTest {
  @Theory
  public void testConstructWithObservations(@ForAll(sampleSize = 50) @From(PartiallyObservedConstructorDataGenerator.class) PartiallyObservedConstructorData data,
                                            @ForAll(sampleSize = 2) @From(ConcatVectorGenerator.class) ConcatVector weights) throws Exception {
    int[] obsArray = new int[9];
    for (int i = 0; i < obsArray.length; i++) obsArray[i] = -1;
    for (int i = 0; i < data.observations.length; i++) {
      obsArray[data.factor.neigborIndices[i]] = data.observations[i];
    }

    TableFactor normalObservations = new TableFactor(weights, data.factor);
    for (int i = 0; i < obsArray.length; i++) {
      if (obsArray[i] != -1) {
        normalObservations = normalObservations.observe(i, obsArray[i]);
      }
    }

    TableFactor constructedObservations = new TableFactor(weights, data.factor, data.observations);

    assertArrayEquals(normalObservations.neighborIndices, constructedObservations.neighborIndices);
    for (int[] assn : normalObservations) {
      assertEquals(normalObservations.getAssignmentValue(assn), constructedObservations.getAssignmentValue(assn), 1.0e-9);
    }
  }

  @Theory
  public void testObserve(@ForAll(sampleSize = 50) @From(TableFactorGenerator.class) TableFactor factor,
                          @ForAll(sampleSize = 2) @InRange(minInt = 0, maxInt = 3) int observe,
                          @ForAll(sampleSize = 2) @InRange(minInt = 0, maxInt = 1) int value) throws Exception {
    if (!Arrays.stream(factor.neighborIndices).boxed().collect(Collectors.toSet()).contains(observe)) return;
    if (factor.neighborIndices.length == 1) return;

    TableFactor observedOut = factor.observe(observe, value);

    int observeIndex = -1;
    for (int i = 0; i < factor.neighborIndices.length; i++) {
      if (factor.neighborIndices[i] == observe) observeIndex = i;
    }

    for (int[] assignment : factor) {
      if (assignment[observeIndex] == value) {
        assertEquals(factor.getAssignmentValue(assignment), observedOut.getAssignmentValue(subsetAssignment(assignment, factor, observedOut)), 1.0e-7);
      }
    }
  }

  @Theory
  public void testGetMaxedMarginals(@ForAll(sampleSize = 50) @From(TableFactorGenerator.class) TableFactor factor,
                                    @ForAll(sampleSize = 10) @InRange(minInt = 0, maxInt = 3) int marginalizeTo) throws Exception {
    if (!Arrays.stream(factor.neighborIndices).boxed().collect(Collectors.toSet()).contains(marginalizeTo)) return;

    int indexOfVariable = -1;
    for (int i = 0; i < factor.neighborIndices.length; i++) {
      if (factor.neighborIndices[i] == marginalizeTo) {
        indexOfVariable = i;
        break;
      }
    }
    assumeTrue(indexOfVariable > -1);

    double[] gold = new double[factor.getDimensions()[indexOfVariable]];
    for (int i = 0; i < gold.length; i++) {
      gold[i] = Double.NEGATIVE_INFINITY;
    }
    for (int[] assignment : factor) {
      gold[assignment[indexOfVariable]] = Math.max(gold[assignment[indexOfVariable]], factor.getAssignmentValue(assignment));
    }

    normalize(gold);

    assertArrayEquals(gold, factor.getMaxedMarginals()[indexOfVariable], 1.0e-5);
  }

  @Theory
  public void testGetSummedMarginals(@ForAll(sampleSize = 50) @From(TableFactorGenerator.class) TableFactor factor,
                                     @ForAll(sampleSize = 10) @InRange(minInt = 0, maxInt = 3) int marginalizeTo) throws Exception {
    if (!Arrays.stream(factor.neighborIndices).boxed().collect(Collectors.toSet()).contains(marginalizeTo)) return;

    int indexOfVariable = -1;
    for (int i = 0; i < factor.neighborIndices.length; i++) {
      if (factor.neighborIndices[i] == marginalizeTo) {
        indexOfVariable = i;
        break;
      }
    }
    assumeTrue(indexOfVariable > -1);

    double[] gold = new double[factor.getDimensions()[indexOfVariable]];
    for (int[] assignment : factor) {
      gold[assignment[indexOfVariable]] = gold[assignment[indexOfVariable]] + factor.getAssignmentValue(assignment);
    }

    normalize(gold);

    assertArrayEquals(gold, factor.getSummedMarginals()[indexOfVariable], 1.0e-5);
  }

  private void normalize(double[] arr) {
    double sum = 0;
    for (double d : arr) sum += d;
    if (sum == 0) {
      for (int i = 0; i < arr.length; i++) {
        arr[i] = 1.0 / arr.length;
      }
    } else {
      for (int i = 0; i < arr.length; i++) {
        arr[i] = arr[i] / sum;
      }
    }
  }

  @Theory
  public void testValueSum(@ForAll(sampleSize = 50) @From(TableFactorGenerator.class) TableFactor factor) throws Exception {
    double sum = 0.0;
    for (int[] assignment : factor) {
      sum += factor.getAssignmentValue(assignment);
    }

    assertEquals(sum, factor.valueSum(), 1.0e-5);
  }

  @Theory
  public void testMaxOut(@ForAll(sampleSize = 50) @From(TableFactorGenerator.class) TableFactor factor,
                         @ForAll(sampleSize = 10) @InRange(minInt = 0, maxInt = 3) int marginalize) throws Exception {
    if (!Arrays.stream(factor.neighborIndices).boxed().collect(Collectors.toSet()).contains(marginalize)) return;
    if (factor.neighborIndices.length <= 1) return;

    TableFactor maxedOut = factor.maxOut(marginalize);

    assertEquals(factor.neighborIndices.length - 1, maxedOut.neighborIndices.length);
    assertTrue(!Arrays.stream(maxedOut.neighborIndices).boxed().collect(Collectors.toSet()).contains(marginalize));

    for (int[] assignment : factor) {
      assertTrue(factor.getAssignmentValue(assignment) >= Double.NEGATIVE_INFINITY);
      assertTrue(factor.getAssignmentValue(assignment) <= maxedOut.getAssignmentValue(subsetAssignment(assignment, factor, maxedOut)));
    }

    Map<List<Integer>, List<int[]>> subsetToSuperset = subsetToSupersetAssignments(factor, maxedOut);
    for (List<Integer> subsetAssignmentList : subsetToSuperset.keySet()) {
      double max = Double.NEGATIVE_INFINITY;
      for (int[] supersetAssignment : subsetToSuperset.get(subsetAssignmentList)) {
        max = Math.max(max, factor.getAssignmentValue(supersetAssignment));
      }

      int[] subsetAssignment = new int[subsetAssignmentList.size()];
      for (int i = 0; i < subsetAssignment.length; i++) {
        subsetAssignment[i] = subsetAssignmentList.get(i);
      }

      assertEquals(max, maxedOut.getAssignmentValue(subsetAssignment), 1.0e-5);
    }
  }

  @Theory
  public void testSumOut(@ForAll(sampleSize = 50) @From(TableFactorGenerator.class) TableFactor factor,
                         @ForAll(sampleSize = 10) @InRange(minInt = 0, maxInt = 3) int marginalize) throws Exception {
    if (!Arrays.stream(factor.neighborIndices).boxed().collect(Collectors.toSet()).contains(marginalize)) return;
    if (factor.neighborIndices.length <= 1) return;

    TableFactor summedOut = factor.sumOut(marginalize);

    assertEquals(factor.neighborIndices.length - 1, summedOut.neighborIndices.length);
    assertTrue(!Arrays.stream(summedOut.neighborIndices).boxed().collect(Collectors.toSet()).contains(marginalize));

    Map<List<Integer>, List<int[]>> subsetToSuperset = subsetToSupersetAssignments(factor, summedOut);
    for (List<Integer> subsetAssignmentList : subsetToSuperset.keySet()) {
      double sum = 0.0;
      for (int[] supersetAssignment : subsetToSuperset.get(subsetAssignmentList)) {
        sum += factor.getAssignmentValue(supersetAssignment);
      }

      int[] subsetAssignment = new int[subsetAssignmentList.size()];
      for (int i = 0; i < subsetAssignment.length; i++) {
        subsetAssignment[i] = subsetAssignmentList.get(i);
      }

      assertEquals(sum, summedOut.getAssignmentValue(subsetAssignment), 1.0e-5);
    }
  }

  @Theory
  public void testMultiply(@ForAll(sampleSize = 10) @From(TableFactorGenerator.class) TableFactor factor1,
                           @ForAll(sampleSize = 10) @From(TableFactorGenerator.class) TableFactor factor2) throws Exception {
    TableFactor result = factor1.multiply(factor2);

    for (int[] assignment : result) {
      double factor1Value = factor1.getAssignmentValue(subsetAssignment(assignment, result, factor1));
      double factor2Value = factor2.getAssignmentValue(subsetAssignment(assignment, result, factor2));
      assertEquals(factor1Value * factor2Value, result.getAssignmentValue(assignment), 1.0e-5);
    }

    // Check for no duplication
    for (int i = 0; i < result.neighborIndices.length; i++) {
      for (int j = 0; j < result.neighborIndices.length; j++) {
        if (i == j) continue;
        assertNotEquals(result.neighborIndices[i], result.neighborIndices[j]);
      }
    }
  }

  public static int[] variableSizes = new int[]{
      2, 4, 2, 3
  };

  public static class TableFactorGenerator extends Generator<TableFactor> {
    public TableFactorGenerator(Class<TableFactor> type) {
      super(type);
    }

    @Override
    public TableFactor generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus) {
      int numNeighbors = sourceOfRandomness.nextInt(1, 3);
      int[] neighbors = new int[numNeighbors];
      int[] dimensions = new int[numNeighbors];
      Set<Integer> usedNeighbors = new HashSet<>();
      for (int i = 0; i < neighbors.length; i++) {
        while (true) {
          int neighbor = sourceOfRandomness.nextInt(0, 3);
          if (!usedNeighbors.contains(neighbor)) {
            usedNeighbors.add(neighbor);
            neighbors[i] = neighbor;
            dimensions[i] = variableSizes[neighbor];
            break;
          }
        }
      }

      // Make sure we get some all-0 factor tables
      double multiple = sourceOfRandomness.nextDouble();

      TableFactor factor = new TableFactor(neighbors, dimensions);
      for (int[] assignment : factor) {
        factor.setAssignmentValue(assignment, multiple * sourceOfRandomness.nextDouble());
      }

      return factor;
    }
  }

  public static class ConcatVectorGenerator extends Generator<ConcatVector> {
    public ConcatVectorGenerator(Class<ConcatVector> type) {
      super(type);
    }

    @Override
    public ConcatVector generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus) {
      ConcatVector vec = new ConcatVector(1);
      double[] d = new double[20];
      for (int i = 0; i < d.length; i++) d[i] = sourceOfRandomness.nextDouble();
      vec.setDenseComponent(0, d);
      return vec;
    }
  }

  private static class PartiallyObservedConstructorData {
    public GraphicalModel.Factor factor;
    public int[] observations;
  }

  public static class PartiallyObservedConstructorDataGenerator extends Generator<PartiallyObservedConstructorData> {
    public PartiallyObservedConstructorDataGenerator(Class<PartiallyObservedConstructorData> type) {
      super(type);
    }

    @Override
    public PartiallyObservedConstructorData generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus) {
      int len = sourceOfRandomness.nextInt(1, 4);
      Set<Integer> taken = new HashSet<>();

      int[] neighborIndices = new int[len];
      int[] dimensions = new int[len];
      int[] observations = new int[len];
      int numObserved = 0;

      for (int i = 0; i < len; i++) {
        int j = sourceOfRandomness.nextInt(8);
        while (taken.contains(j)) {
          j = sourceOfRandomness.nextInt(8);
        }
        taken.add(j);
        neighborIndices[i] = j;

        dimensions[i] = sourceOfRandomness.nextInt(1, 3);

        if (sourceOfRandomness.nextBoolean() && numObserved + 1 < dimensions.length) {
          observations[i] = sourceOfRandomness.nextInt(dimensions[i]);
          numObserved++;
        } else observations[i] = -1;
      }

      ConcatVectorTable t = new ConcatVectorTable(dimensions);

      ConcatVectorGenerator gen = new ConcatVectorGenerator(ConcatVector.class);

      for (int[] assn : t) {
        ConcatVector vec = gen.generate(sourceOfRandomness, generationStatus);
        t.setAssignmentValue(assn, () -> vec);
      }

      PartiallyObservedConstructorData data = new PartiallyObservedConstructorData();
      data.factor = new GraphicalModel.Factor(t, neighborIndices);
      data.observations = observations;

      return data;
    }
  }

  /**
   * Takes a full assignment from a superset factor, and figures out how to map it into a subset factor. This is very
   * useful for testing that functional properties are not violated across both product and marginalization steps.
   *
   * @param supersetAssignment the assignment in the superset factor
   * @param superset           the superset factor, containing the variables from the subset
   * @param subset             the subset factor, containing some of the variables found in the superset
   * @return an assignment into the subset factor
   */
  private int[] subsetAssignment(int[] supersetAssignment, TableFactor superset, TableFactor subset) {
    int[] subsetAssignment = new int[subset.neighborIndices.length];
    for (int i = 0; i < subset.neighborIndices.length; i++) {
      int var = subset.neighborIndices[i];
      subsetAssignment[i] = -1;
      for (int j = 0; j < superset.neighborIndices.length; j++) {
        if (superset.neighborIndices[j] == var) {
          subsetAssignment[i] = supersetAssignment[j];
          break;
        }
      }
      assert (subsetAssignment[i] != -1);
    }
    return subsetAssignment;
  }

  /**
   * Convenience function to construct a subset to superset assignment map. Each subset assignment will be mapping
   * to a large number of superset assignments.
   *
   * @param superset the superset factor to map to
   * @param subset   the subset factor to map from
   * @return a map from subset assignment to list of superset assignment
   */
  private Map<List<Integer>, List<int[]>> subsetToSupersetAssignments(TableFactor superset, TableFactor subset) {
    Map<List<Integer>, List<int[]>> subsetToSupersets = new HashMap<>();
    for (int[] assignment : subset) {
      List<Integer> subsetAssignmentList = Arrays.stream(assignment).boxed().collect(Collectors.toList());
      List<int[]> supersetAssignments = new ArrayList<>();
      for (int[] supersetAssignment : superset) {
        if (Arrays.equals(assignment, subsetAssignment(supersetAssignment, superset, subset))) {
          supersetAssignments.add(supersetAssignment);
        }
      }
      subsetToSupersets.put(subsetAssignmentList, supersetAssignments);
    }
    return subsetToSupersets;
  }
}