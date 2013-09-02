package edu.stanford.nlp.optimization;

import edu.stanford.nlp.math.ArrayMath;

import java.util.Arrays;

/**
 * A class to test whether a DiffFunction's derivative is correct.
 *
 * @author Galen Andrew
 */
public class DiffFunctionTester {

  static private double EPS = 1e-8;

  static private double[] testGrad(Function f, double[] x, int[] testIndices, double eps) {
    double val = f.valueAt(x);
    double[] answer = new double[testIndices.length];
    for (int i = 0; i < testIndices.length; i++) {
      x[testIndices[i]] += eps;
      //System.err.println("new x is "+x[testIndices[i]]);
      answer[i] = (f.valueAt(x) - val) / eps;
      x[testIndices[i]] -= eps;
      System.err.print(".");
      //System.err.print(" "+answer[i]);
    }

    return answer;
  }

  static private double[] testGrad(Function f, double[] x, int[] testIndices) {
    return testGrad(f, x, testIndices, EPS);
  }

  static public void test(DiffFunction f) {
    for (int deg = -2; deg > -10; deg--) {
      double eps = Math.pow(10, deg);
      System.err.println("testing for eps " + eps);
      test(f, eps);
    }
  }

  static public void test(DiffFunction f, double eps) {
    double x[] = new double[f.domainDimension()];
    for (int i = 0; i < x.length; i++) {
      x[i] = 1e-3;// (i - 0.5) * 4;
    }
    test(f, x, eps);
  }

  public static void test(DiffFunction f, double[] x, double eps) {
    int ind[] = new int[10];
    ind[0] = x.length - 1;
    for (int i = 1; i < ind.length; i++) {
      //ind[i] = (int)(Math.random() * x.length);
      ind[i] = i;
    }
    test(f, x, ind, eps);
  }

  public static void test(DiffFunction f, double[] x, int[] ind, double eps) {
    System.err.print("Testing grad <");
    double[] testGrad = testGrad(f, x, ind, eps);
    System.err.println(">");
    double[] fullGrad = f.derivativeAt(x);
    double[] fGrad = new double[ind.length];
    for (int i = 0; i < ind.length; i++) {
      fGrad[i] = fullGrad[ind[i]];
    }
    double[] diff = ArrayMath.pairwiseSubtract(testGrad, fGrad);
    System.err.println("1-norm:" + ArrayMath.norm_1(diff));
    System.err.println("2-norm:" + ArrayMath.norm(diff));
    System.err.println("inf-norm:" + ArrayMath.norm_inf(diff));
    System.err.println("pearson:" + ArrayMath.pearsonCorrelation(testGrad,fGrad));
    ArrayMath.standardize(fGrad);
    ArrayMath.standardize(testGrad);
    System.err.printf("test: %s\n",Arrays.toString(testGrad));
    System.err.printf("full: %s\n",Arrays.toString(fGrad));
  }

  public static void main(String[] args) {

    test(new DiffFunction() {
      public double[] derivativeAt(double[] x) {
        return ArrayMath.add(ArrayMath.multiply(x, 2), 1);
      }

      public double valueAt(double[] x) {
        return ArrayMath.innerProduct(x, ArrayMath.add(x, 1));
      }

      public int domainDimension() {
        return 10000;
      }
    });
  }
}
