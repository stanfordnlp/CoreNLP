package edu.stanford.nlp.optimization;

import java.util.Random;

import junit.framework.TestCase;

import edu.stanford.nlp.math.ArrayMath;

/**
 * This class both tests a particular DiffFunction and provides a basis
 * for testing whether any DiffFunction's derivative is correct.
 *
 * @author Galen Andrew
 * @author Christopher Manning
 */
public class DiffFunctionTest extends TestCase {

  // private static final double EPS = 1e-6;
  private static final Random r = new Random();

  private static double[] estimateGradient(Function f, double[] x, int[] testIndices, double eps) {
    double[] lowAnswer = new double[testIndices.length];
    double[] answer = new double[testIndices.length];
    for (int i = 0; i < testIndices.length; i++) {
      double orig = x[testIndices[i]];
      x[testIndices[i]] -= eps;
      lowAnswer[i] = f.valueAt(x);
      x[testIndices[i]] = orig + eps;
      answer[i] = f.valueAt(x);
      x[testIndices[i]] = orig; // restore value
      //System.err.println("new x is "+x[testIndices[i]]);
      answer[i] = (answer[i] - lowAnswer[i]) / (2.0 * eps);
      // System.err.print(".");
      //System.err.print(" "+answer[i]);
    }
    // System.err.println("Gradient estimate is: " + Arrays.toString(answer));
    return answer;
  }

  public static void gradientCheck(DiffFunction f) {
    for (int deg = -2; deg > -7; deg--) {
      double eps = Math.pow(10, deg);
      System.err.println("testing for eps " + eps);
      gradientCheck(f, eps);
    }
  }

  public static void gradientCheck(DiffFunction f, double eps) {
    double[] x = new double[f.domainDimension()];
    for (int i = 0; i < x.length; i++) {
      x[i] = Math.random() - 0.5; // 0.03; (i - 0.5) * 4;
    }
    gradientCheck(f, x, eps);
  }

  public static void gradientCheck(DiffFunction f, double[] x, double eps) {
    // just check a few dimensions
    int numDim = Math.min(10, x.length);
    int[] ind = new int[numDim];
    if (numDim == x.length) {
      for (int i = 0; i < ind.length; i++) {
        ind[i] = i;
      }
    } else {
      ind[0] = 0;
      ind[1] =  x.length - 1;
      for (int i = 2; i < ind.length; i++) {
        ind[i] = r.nextInt(x.length - 2) + 1;
        // ind[i] = i;
      }
    }
    gradientCheck(f, x, ind, eps);
  }

  public static void gradientCheck(DiffFunction f, double[] x, int[] ind, double eps) {
    // System.err.print("Testing grad <");
    double[] testGrad = estimateGradient(f, x, ind, eps);
    // System.err.println(">");
    double[] fullGrad = f.derivativeAt(x);
    double[] fGrad = new double[ind.length];
    for (int i = 0; i < ind.length; i++) {
      fGrad[i] = fullGrad[ind[i]];
    }

    double[] diff = ArrayMath.pairwiseSubtract(testGrad, fGrad);
    System.err.println("1-norm:" + ArrayMath.norm_1(diff));
    assertEquals(0.0, ArrayMath.norm_1(diff), 2 * eps);
    System.err.println("2-norm:" + ArrayMath.norm(diff));
    assertEquals(0.0, ArrayMath.norm(diff), 2 * eps);
    System.err.println("inf-norm:" + ArrayMath.norm_inf(diff));
    assertEquals(0.0, ArrayMath.norm_inf(diff), 2 * eps);
    System.err.println("pearson:" + ArrayMath.pearsonCorrelation(testGrad,fGrad));
    assertEquals(1.0, ArrayMath.pearsonCorrelation(testGrad,fGrad), 2 * eps);
    // This could exception if all numbers were the same and so there is no standard deviation.
    // ArrayMath.standardize(fGrad);
    // ArrayMath.standardize(testGrad);

    // System.err.printf("test: %s%n", Arrays.toString(testGrad));
    // System.err.printf("full: %s%n",Arrays.toString(fGrad));
  }

  public void testXSquaredPlusOne() {
    gradientCheck(new DiffFunction() {
      // this function does on a large vector x^2+1
      @Override
      public double[] derivativeAt(double[] x) {
        return ArrayMath.add(ArrayMath.multiply(x, 2), 1);
      }

      @Override
      public double valueAt(double[] x) {
        return ArrayMath.innerProduct(x, ArrayMath.add(x, 1));
      }

      @Override
      public int domainDimension() {
        return 10000;
      }
    });
  }

}
