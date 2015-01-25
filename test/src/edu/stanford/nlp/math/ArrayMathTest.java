package edu.stanford.nlp.math;

import junit.framework.TestCase;

public class ArrayMathTest extends TestCase {
  private double[] d1 = new double[3];
  private double[] d2 = new double[3];
  private double[] d3 = new double[3];
  private double[] d4 = new double[3];
  private double[] d5 = new double[4];

  @Override
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

  public void testInnerProduct() {
    double inner = ArrayMath.innerProduct(d4, d4);
    assertEquals("Wrong inner product", 0.14, inner, 1e-6);
    inner = ArrayMath.innerProduct(d5, d5);
    assertEquals("Wrong inner product", 0.78, inner, 1e-6);
  }

  public void testNumRows() {
    int nRows = ArrayMath.numRows(d1);
    assertEquals(nRows, 3);
  }

  public void testExpLog() {
    double[] d1prime = ArrayMath.log(ArrayMath.exp(d1));
    double[] diff = ArrayMath.pairwiseSubtract(d1, d1prime);
    double norm2 = ArrayMath.norm(diff);
    assertTrue(norm2 < 1e-4);
  }

  public void testExpLogInplace() {
    ArrayMath.expInPlace(d1);
    ArrayMath.logInPlace(d1);

    ArrayMath.pairwiseSubtractInPlace(d1, d2);
    double norm2 = ArrayMath.norm(d1);
    assertTrue(norm2 < 1e-4);
  }

  public void testAddInPlace() {
    ArrayMath.addInPlace(d1, 3);
    for (int i = 0; i < ArrayMath.numRows(d1); i++) {
      assertTrue(d1[i]==d2[i]+3);
    }
  }

  public void testMultiplyInPlace() {
    ArrayMath.multiplyInPlace(d1, 3);
    for (int i = 0; i < ArrayMath.numRows(d1); i++) {
      assertTrue(d1[i]==d2[i]*3);
    }
  }

  public void testPowInPlace() {
    ArrayMath.powInPlace(d1, 3);
    for (int i = 0; i < ArrayMath.numRows(d1); i++) {
      assertTrue(d1[i]==Math.pow(d2[i],3));
    }
  }

  public void testAdd() {
    double[] d1prime = ArrayMath.add(d1, 3);
    for (int i = 0; i < ArrayMath.numRows(d1prime); i++) {
      assertTrue(d1prime[i]==d1[i]+3);
    }
  }

  public void testMultiply() {
    double[] d1prime = ArrayMath.multiply(d1, 3);
    for (int i = 0; i < ArrayMath.numRows(d1prime); i++) {
      assertTrue(d1prime[i]==d1[i]*3);
    }
  }

  public void testPow() {
    double[] d1prime = ArrayMath.pow(d1, 3);
    for (int i = 0; i < ArrayMath.numRows(d1prime); i++) {
      assertTrue(d1prime[i]==Math.pow(d1[i],3));
    }
  }

  public void testPairwiseAdd() {
    double[] sum = ArrayMath.pairwiseAdd(d1,d2);
    for (int i = 0; i < ArrayMath.numRows(d1); i++) {
      assertTrue(sum[i] == d1[i]+d2[i]);
    }
  }

  public void testPairwiseSubtract() {
    double[] diff = ArrayMath.pairwiseSubtract(d1,d2);
    for (int i = 0; i < ArrayMath.numRows(d1); i++) {
      assertTrue(diff[i] == d1[i]-d2[i]);
    }
  }

  public void testPairwiseMultiply() {
    double[] product = ArrayMath.pairwiseMultiply(d1,d2);
    for (int i = 0; i < ArrayMath.numRows(d1); i++) {
      assertTrue(product[i] == d1[i]*d2[i]);
    }
  }

  public void testHasNaN() {
    assertFalse(ArrayMath.hasNaN(d1));
    assertFalse(ArrayMath.hasNaN(d2));
    assertTrue(ArrayMath.hasNaN(d3));
  }

  public void testHasInfinite() {
    assertFalse(ArrayMath.hasInfinite(d1));
    assertFalse(ArrayMath.hasInfinite(d2));
    assertTrue(ArrayMath.hasInfinite(d3));
  }

  public void testCountNaN() {
    assertTrue(ArrayMath.countNaN(d1)==0);
    assertTrue(ArrayMath.countNaN(d2)==0);
    assertTrue(ArrayMath.countNaN(d3)==1);
  }

  public void testFliterNaN() {
    double[] f_d3 = ArrayMath.filterNaN(d3);
    assertTrue(ArrayMath.numRows(f_d3)==2);
    assertTrue(ArrayMath.countNaN(f_d3)==0);
  }

  public void testCountInfinite() {
    assertTrue(ArrayMath.countInfinite(d1)==0);
    assertTrue(ArrayMath.countInfinite(d2)==0);
    assertTrue(ArrayMath.countInfinite(d3)==1);
  }

  public void testFliterInfinite() {
    double[] f_d3 = ArrayMath.filterInfinite(d3);
    assertTrue(ArrayMath.numRows(f_d3)==2);
    assertTrue(ArrayMath.countInfinite(f_d3)==0);
  }

  public void testFliterNaNAndInfinite() {
    double[] f_d3 = ArrayMath.filterNaNAndInfinite(d3);
    assertTrue(ArrayMath.numRows(f_d3)==1);
    assertTrue(ArrayMath.countInfinite(f_d3)==0);
    assertTrue(ArrayMath.countNaN(f_d3)==0);
  }

  public void testSum() {
    double sum = ArrayMath.sum(d1);
    double mySum = 0.0;
    for (double d : d1) {
      mySum += d;
    }
    assertTrue(sum==mySum);
  }

  public void testNorm_inf() {
    double ninf = ArrayMath.norm_inf(d1);
    double max = ArrayMath.max(d1);
    assertTrue(ninf==max);
    ninf = ArrayMath.norm_inf(d2);
    max = ArrayMath.max(d2);
    assertTrue(ninf==max);
    ninf = ArrayMath.norm_inf(d3);
    max = ArrayMath.max(d3);
    assertTrue(ninf==max);
  }

  public void testArgmax() {
    assertTrue(ArrayMath.max(d1)==d1[ArrayMath.argmax(d1)]);
    assertTrue(ArrayMath.max(d2)==d2[ArrayMath.argmax(d2)]);
    assertTrue(ArrayMath.max(d3)==d3[ArrayMath.argmax(d3)]);
  }

  public void testArgmin() {
    assertTrue(ArrayMath.min(d1)==d1[ArrayMath.argmin(d1)]);
    assertTrue(ArrayMath.min(d2)==d2[ArrayMath.argmin(d2)]);
    assertTrue(ArrayMath.min(d3)==d3[ArrayMath.argmin(d3)]);
  }

  public void testLogSum() {
    double lsum = ArrayMath.logSum(d4);
    double myLsum = 0;
    for (double d : d4) {
      myLsum += Math.exp(d);
    }
    myLsum = Math.log(myLsum);
    assertTrue(myLsum == lsum);
  }

  public void testNormalize() {
    double tol = 1e-4;
    ArrayMath.normalize(d1);
    ArrayMath.normalize(d2);
    //ArrayMath.normalize(d3);
    ArrayMath.normalize(d4);
    assertTrue(ArrayMath.sum(d1)-1 < tol);
    assertTrue(ArrayMath.sum(d2)-1 < tol);
    //assertTrue(ArrayMath.sum(d3)-1 < tol);
    assertTrue(ArrayMath.sum(d4)-1 < tol);
  }

  public void testKLDivergence() {
    double kld = ArrayMath.klDivergence(d1, d2);
    assertTrue(kld==0);
  }

  public void testSumAndMean() {
    assertTrue(ArrayMath.sum(d1) == ArrayMath.mean(d1)*d1.length);
    assertTrue(ArrayMath.sum(d2) == ArrayMath.mean(d2)*d2.length);
    //assertTrue(ArrayMath.sum(d3) == ArrayMath.mean(d3)*d3.length);
    assertTrue(ArrayMath.sum(d4) == ArrayMath.mean(d4)*d4.length);
  }

  public static void helpTestSafeSumAndMean(double[] d) {
    double[] dprime = ArrayMath.filterNaNAndInfinite(d);
    assertTrue(ArrayMath.safeMean(d)*ArrayMath.numRows(dprime)==ArrayMath.sum(dprime));
  }

  public void testSafeSumAndMean() {
    helpTestSafeSumAndMean(d1);
    helpTestSafeSumAndMean(d2);
    helpTestSafeSumAndMean(d3);
    helpTestSafeSumAndMean(d4);
  }

  public void testJensenShannon() {
    double[] a = { 0.1, 0.1, 0.7, 0.1, 0.0, 0.0 };
    double[] b = { 0.0, 0.1, 0.1, 0.7, 0.1, 0.0 };
    assertEquals(0.46514844544032313, ArrayMath.jensenShannonDivergence(a, b), 1e-5);

    double[] c = { 1.0, 0.0, 0.0 };
    double[] d = { 0.0, 0.5, 0.5 };
    assertEquals(1.0, ArrayMath.jensenShannonDivergence(c, d), 1e-5);
  }

}
