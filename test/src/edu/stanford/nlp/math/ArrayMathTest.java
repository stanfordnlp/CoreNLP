package edu.stanford.nlp.math;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ArrayMathTest {

  private double[] d1 = new double[3];
  private double[] d2 = new double[3];
  private double[] d3 = new double[3];
  private double[] d4 = new double[3];
  private double[] d5 = new double[4];

  @Before
  public void setUp() {
    d1[0] = 1.0;
    d1[1] = 343.33;
    d1[2] = -13.1;
    d2[0] = 1.0;
    d2[1] = 343.33;
    d2[2] = -13.1;
    d3[0] = Double.NaN;
    d3[1] = Double.POSITIVE_INFINITY ;
    d3[2] = 2;
    d4[0] = 0.1;
    d4[1] = 0.2;
    d4[2] = 0.3;
    d5[0] = 0.1;
    d5[1] = 0.2;
    d5[2] = 0.3;
    d5[3] = 0.8;
  }

  @Test
  public void testInnerProduct() {
    double inner = ArrayMath.innerProduct(d4, d4);
    Assert.assertEquals("Wrong inner product", 0.14, inner, 1e-6);
    inner = ArrayMath.innerProduct(d5, d5);
    Assert.assertEquals("Wrong inner product", 0.78, inner, 1e-6);
  }

  @Test
  public void testNumRows() {
    int nRows = ArrayMath.numRows(d1);
    Assert.assertEquals(nRows, 3);
  }

  @Test
  public void testExpLog() {
    double[] d1prime = ArrayMath.log(ArrayMath.exp(d1));
    double[] diff = ArrayMath.pairwiseSubtract(d1, d1prime);
    double norm2 = ArrayMath.norm(diff);
    Assert.assertEquals(0.0, norm2, 1e-5);
  }

  @Test
  public void testExpLogInplace() {
    ArrayMath.expInPlace(d1);
    ArrayMath.logInPlace(d1);

    ArrayMath.pairwiseSubtractInPlace(d1, d2);
    double norm2 = ArrayMath.norm(d1);
    Assert.assertEquals(0.0, norm2,  1e-5);
  }

  @Test
  public void testAddInPlace() {
    ArrayMath.addInPlace(d1, 3);
    for (int i = 0; i < ArrayMath.numRows(d1); i++) {
      Assert.assertEquals(d1[i], d2[i] + 3, 1e-5);
    }
  }

  @Test
  public void testMultiplyInPlace() {
    ArrayMath.multiplyInPlace(d1, 3);
    for (int i = 0; i < ArrayMath.numRows(d1); i++) {
      Assert.assertEquals(d1[i], d2[i] * 3, 1e-5);
    }
  }

  @Test
  public void testPowInPlace() {
    ArrayMath.powInPlace(d1, 3);
    for (int i = 0; i < ArrayMath.numRows(d1); i++) {
      Assert.assertEquals(d1[i], Math.pow(d2[i], 3), 1e-5);
    }
  }

  @Test
  public void testAdd() {
    double[] d1prime = ArrayMath.add(d1, 3);
    for (int i = 0; i < ArrayMath.numRows(d1prime); i++) {
      Assert.assertEquals(d1prime[i], d1[i] + 3, 1e-5);
    }
  }

  @Test
  public void testMultiply() {
    double[] d1prime = ArrayMath.multiply(d1, 3);
    for (int i = 0; i < ArrayMath.numRows(d1prime); i++) {
      Assert.assertEquals(d1prime[i], d1[i] * 3, 1e-5);
    }
  }

  @Test
  public void testPow() {
    double[] d1prime = ArrayMath.pow(d1, 3);
    for (int i = 0; i < ArrayMath.numRows(d1prime); i++) {
      Assert.assertEquals(d1prime[i], Math.pow(d1[i], 3), 1e-5);
    }
  }

  @Test
  public void testPairwiseAdd() {
    double[] sum = ArrayMath.pairwiseAdd(d1,d2);
    for (int i = 0; i < ArrayMath.numRows(d1); i++) {
      Assert.assertEquals(sum[i], d1[i] + d2[i], 1e-5);
    }
  }

  @Test
  public void testPairwiseSubtract() {
    double[] diff = ArrayMath.pairwiseSubtract(d1,d2);
    for (int i = 0; i < ArrayMath.numRows(d1); i++) {
      Assert.assertEquals(diff[i], d1[i] - d2[i], 1e-5);
    }
  }

  @Test
  public void testPairwiseMultiply() {
    double[] product = ArrayMath.pairwiseMultiply(d1,d2);
    for (int i = 0; i < ArrayMath.numRows(d1); i++) {
      Assert.assertEquals(product[i], d1[i] * d2[i], 1e-5);
    }
  }

  @Test
  public void testHasNaN() {
    Assert.assertFalse(ArrayMath.hasNaN(d1));
    Assert.assertFalse(ArrayMath.hasNaN(d2));
    Assert.assertTrue(ArrayMath.hasNaN(d3));
  }

  @Test
  public void testHasInfinite() {
    Assert.assertFalse(ArrayMath.hasInfinite(d1));
    Assert.assertFalse(ArrayMath.hasInfinite(d2));
    Assert.assertTrue(ArrayMath.hasInfinite(d3));
  }

  @Test
  public void testCountNaN() {
    Assert.assertEquals(ArrayMath.countNaN(d1), 0);
    Assert.assertEquals(ArrayMath.countNaN(d2), 0);
    Assert.assertEquals(ArrayMath.countNaN(d3), 1);
  }

  @Test
  public void testFliterNaN() {
    double[] f_d3 = ArrayMath.filterNaN(d3);
    Assert.assertEquals(ArrayMath.numRows(f_d3), 2);
    Assert.assertEquals(ArrayMath.countNaN(f_d3), 0);
  }

  @Test
  public void testCountInfinite() {
    Assert.assertEquals(ArrayMath.countInfinite(d1), 0);
    Assert.assertEquals(ArrayMath.countInfinite(d2), 0);
    Assert.assertEquals(ArrayMath.countInfinite(d3), 1);
  }

  @Test
  public void testFliterInfinite() {
    double[] f_d3 = ArrayMath.filterInfinite(d3);
    Assert.assertEquals(ArrayMath.numRows(f_d3), 2);
    Assert.assertEquals(ArrayMath.countInfinite(f_d3), 0);
  }

  @Test
  public void testFliterNaNAndInfinite() {
    double[] f_d3 = ArrayMath.filterNaNAndInfinite(d3);
    Assert.assertEquals(ArrayMath.numRows(f_d3), 1);
    Assert.assertEquals(ArrayMath.countInfinite(f_d3), 0);
    Assert.assertEquals(ArrayMath.countNaN(f_d3), 0);
  }

  @Test
  public void testSum() {
    double sum = ArrayMath.sum(d1);
    double mySum = 0.0;
    for (double d : d1) {
      mySum += d;
    }
    Assert.assertEquals(sum, mySum, 1e-6);
  }

  @Test
  public void testNorm_inf() {
    double ninf = ArrayMath.norm_inf(d1);
    double max = ArrayMath.max(d1);
    Assert.assertEquals(ninf, max, 1e-6);
    ninf = ArrayMath.norm_inf(d2);
    max = ArrayMath.max(d2);
    Assert.assertEquals(ninf, max, 1e-6);
    ninf = ArrayMath.norm_inf(d3);
    max = ArrayMath.max(d3);
    Assert.assertEquals(ninf, max, 1e-6);
  }

  @Test
  public void testArgmax() {
    Assert.assertEquals(ArrayMath.max(d1), d1[ArrayMath.argmax(d1)], 1e-5);
    Assert.assertEquals(ArrayMath.max(d2), d2[ArrayMath.argmax(d2)], 1e-5);
    Assert.assertEquals(ArrayMath.max(d3), d3[ArrayMath.argmax(d3)], 1e-5);
  }

  @Test
  public void testArgmin() {
    Assert.assertEquals(ArrayMath.min(d1), d1[ArrayMath.argmin(d1)], 1e-5);
    Assert.assertEquals(ArrayMath.min(d2), d2[ArrayMath.argmin(d2)], 1e-5);
    Assert.assertEquals(ArrayMath.min(d3), d3[ArrayMath.argmin(d3)], 1e-5);
  }

  @Test
  public void testLogSum() {
    double lsum = ArrayMath.logSum(d4);
    double myLsum = 0;
    for (double d : d4) {
      myLsum += Math.exp(d);
    }
    myLsum = Math.log(myLsum);
    Assert.assertEquals(myLsum, lsum, 1e-5);
  }

  @Test
  public void testNormalize() {
    double tol = 1e-5;
    ArrayMath.normalize(d1);
    ArrayMath.normalize(d2);
    //ArrayMath.normalize(d3);
    ArrayMath.normalize(d4);
    Assert.assertEquals(1.0, ArrayMath.sum(d1), tol);
    Assert.assertEquals(1.0, ArrayMath.sum(d2), tol);
    // assertEquals(1.0, ArrayMath.sum(d3), tol);
    Assert.assertEquals(1.0, ArrayMath.sum(d4), tol);
  }

  @Test
  public void testKLDivergence() {
    double kld = ArrayMath.klDivergence(d1, d2);
    Assert.assertEquals(0.0, kld, 1e-5);
  }

  @Test
  public void testSumAndMean() {
    Assert.assertEquals(ArrayMath.sum(d1), ArrayMath.mean(d1) * d1.length, 1e-5);
    Assert.assertEquals(ArrayMath.sum(d2), ArrayMath.mean(d2) * d2.length, 1e-5);
    Assert.assertEquals(ArrayMath.sum(d3), ArrayMath.mean(d3) * d3.length, 1e-5); // comes out as NaN but works!
    Assert.assertEquals(ArrayMath.sum(d4), ArrayMath.mean(d4) * d4.length, 1e-5);
  }

  private static void helpTestSafeSumAndMean(double[] d) {
    double[] dprime = ArrayMath.filterNaNAndInfinite(d);
    Assert.assertEquals(ArrayMath.safeMean(d) * ArrayMath.numRows(dprime), ArrayMath.sum(dprime), 1e-5);
  }

  @Test
  public void testSafeSumAndMean() {
    helpTestSafeSumAndMean(d1);
    helpTestSafeSumAndMean(d2);
    helpTestSafeSumAndMean(d3);
    helpTestSafeSumAndMean(d4);
  }

  @Test
  public void testJensenShannon() {
    double[] a = { 0.1, 0.1, 0.7, 0.1, 0.0, 0.0 };
    double[] b = { 0.0, 0.1, 0.1, 0.7, 0.1, 0.0 };
    Assert.assertEquals(0.46514844544032313, ArrayMath.jensenShannonDivergence(a, b), 1e-5);

    double[] c = { 1.0, 0.0, 0.0 };
    double[] d = { 0.0, 0.5, 0.5 };
    Assert.assertEquals(1.0, ArrayMath.jensenShannonDivergence(c, d), 1e-5);
  }

  @Test
  public void test2dAdd() {
    double[][] d6 = new double[][]{{0.26, 0.87, -1.26}, {0.17, 3.21, -1.8}};
    double[][] d7 = new double[][]{{0.26, 0.07, -1.26}, {0.17, -3.21, -1.8}};
    double[][] d8 = new double[][]{{0.52, 0.94, -2.52}, {0.34, 0.0, -3.6}};
    ArrayMath.addInPlace(d6, d7);
    for (int i = 0; i < d8.length; i++) {
      for (int j = 0; j < d8[i].length; j++) {
        Assert.assertEquals(d6[i][j], d8[i][j], 1e-5);
      }
    }
  }

}
