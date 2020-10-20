package edu.stanford.nlp.classify;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

public class LogisticUtilsTest {

  @Test
  public void testSizeThreeIdentityMatrix() {
    int size = 3;

    int[][] expectedMatrix = {{0}, {1}, {2}};
    int[][] actualMatrix = LogisticUtils.identityMatrix(size);

    Assert.assertArrayEquals(expectedMatrix, actualMatrix);
  }

  @Test
  public void testFlattenArray() {
    double[][] inputArray = {{0.0, 0.1, 0.1}, {1.0, 1.1, 1.2}, {2.0, 2.1, 2.2}};

    double[] expectedArray = {0.0, 0.1, 0.1, 1.0, 1.1, 1.2, 2.0, 2.1, 2.2};
    double[] actualArray = LogisticUtils.flatten(inputArray);

    Assert.assertTrue(Arrays.equals(expectedArray, actualArray));
  }

  @Test
  public void testDotProduct() {
    double[] array1 = {2.5, 1.0, 3.2};
    double[] array2 = {4.0, 5.1, 10.2};
    int[] indicesToMultiply = {0, 2};

    double expectedProduct = 26.32;
    double actualProduct = LogisticUtils.dotProduct(array1, indicesToMultiply, array2);

    Assert.assertEquals(expectedProduct, actualProduct, 0.0001);
  }

  @Test
  public void testConvertCollectionToArray() {
    ArrayList<Double> inputCollection = new ArrayList<>();
    inputCollection.add(1.1);
    inputCollection.add(1.2);
    inputCollection.add(1.3);

    double[] expectedArray = {1.1, 1.2, 1.3};
    double[] actualArray = LogisticUtils.convertToArray(inputCollection);

    Assert.assertTrue(Arrays.equals(expectedArray, actualArray));
  }
}
