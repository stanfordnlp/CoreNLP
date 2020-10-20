package edu.stanford.nlp.loglinear.model;

import com.pholser.junit.quickcheck.ForAll;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.junit.Assert;
import org.junit.contrib.theories.Theories;
import org.junit.contrib.theories.Theory;
import org.junit.runner.RunWith;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;

/**
 * Created on 8/12/15.
 * @author keenon
 * <p>
 * Quickchecking NDArray means hammering on the two bits that are important: assignment iterator, and assignment itself.
 */
@RunWith(Theories.class)
public class NDArrayTest {
  @Theory
  public void testAssignmentsIterator(@ForAll(sampleSize = 50) @From(NDArrayGenerator.class) NDArrayWithGold<Double> testPair) throws Exception {
    Set<List<Integer>> assignmentSet = new HashSet<>();
    for (int[] assignment : testPair.gold.keySet()) {
      assignmentSet.add(Arrays.stream(assignment).boxed().collect(Collectors.toList()));
    }

    for (int[] assignment : testPair.array) {
      List<Integer> l = new ArrayList<>();
      for (int i : assignment) {
        l.add(i);
      }
      assertTrue(assignmentSet.contains(l));
      assignmentSet.remove(l);
    }

    assertTrue(assignmentSet.isEmpty());
  }

  @Theory
  public void testReadWrite(@ForAll(sampleSize = 50) @From(NDArrayGenerator.class) NDArrayWithGold<Double> testPair) throws Exception {
    for (int[] assignment : testPair.gold.keySet()) {
      Assert.assertEquals(testPair.gold.get(assignment), testPair.array.getAssignmentValue(assignment), 1.0e-5);
    }
  }

  @Theory
  public void testClone(@ForAll(sampleSize = 50) @From(NDArrayGenerator.class) NDArrayWithGold<Double> testPair) throws Exception {
    NDArray<Double> clone = testPair.array.cloneArray();
    for (int[] assignment : testPair.gold.keySet()) {
      Assert.assertEquals(testPair.gold.get(assignment), clone.getAssignmentValue(assignment), 1.0e-5);
    }
  }

  public static class NDArrayWithGold<T> {
    public NDArray<T> array;
    public Map<int[], T> gold = new HashMap<>();
  }

  public static class NDArrayGenerator extends Generator<NDArrayWithGold<Double>> {
    public NDArrayGenerator(Class<NDArrayWithGold<Double>> type) {
      super(type);
    }

    @Override
    public NDArrayWithGold<Double> generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus) {
      NDArrayWithGold<Double> testPair = new NDArrayWithGold<>();

      int numDimensions = sourceOfRandomness.nextInt(1, 5);
      int[] dimensions = new int[numDimensions];
      for (int i = 0; i < dimensions.length; i++) {
        dimensions[i] = sourceOfRandomness.nextInt(1, 4);
      }

      testPair.array = new NDArray<>(dimensions);
      recursivelyFillArray(new ArrayList<>(), testPair, sourceOfRandomness);

      return testPair;
    }

    private static void recursivelyFillArray(List<Integer> assignmentSoFar, NDArrayWithGold<Double> testPair, SourceOfRandomness sourceOfRandomness) {
      if (assignmentSoFar.size() == testPair.array.getDimensions().length) {
        int[] arr = new int[assignmentSoFar.size()];
        for (int i = 0; i < arr.length; i++) {
          arr[i] = assignmentSoFar.get(i);
        }

        double value = sourceOfRandomness.nextDouble();
        testPair.array.setAssignmentValue(arr, value);
        testPair.gold.put(arr, value);
      } else {
        for (int i = 0; i < testPair.array.getDimensions()[assignmentSoFar.size()]; i++) {
          List<Integer> newList = new ArrayList<>();
          newList.addAll(assignmentSoFar);
          newList.add(i);
          recursivelyFillArray(newList, testPair, sourceOfRandomness);
        }
      }
    }
  }
}