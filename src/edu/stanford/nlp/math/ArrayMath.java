package edu.stanford.nlp.math;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.RuntimeInterruptedException;
import edu.stanford.nlp.util.StringUtils;

/**
 * Methods for operating on numerical arrays as vectors and matrices.
 *
 * @author Teg Grenager
 */
public class ArrayMath {

  private static final Random rand = new Random();


  private ArrayMath() { } // not instantiable


  // BASIC INFO -----------------------------------------------------------------

  public static int numRows(double[] v) {
    return v.length;
  }

  // GENERATION -----------------------------------------------------------------

  /**
   * Generate a range of integers from start (inclusive) to end (exclusive).
   * Similar to the Python range() builtin function.
   *
   * @param end Ending number (exclusive)
   * @return integers from [0...end)
   */
  public static int[] range(int end) {
    assert end >= 0;
    int[] range = new int[end];
    for (int i = 0; i < end; ++i)
      range[i] = i;
    return range;
  }

  /**
   * Generate a range of integers from start (inclusive) to end (exclusive).
   * Similar to the Python range() builtin function.
   *
   * @param start Beginning number (inclusive)
   * @param end Ending number (exclusive)
   * @return integers from [start...end)
   */
  public static int[] range(int start, int end) {
    assert end >= start;
    int len = end - start;
    int[] range = new int[len];
    for (int i = 0; i < range.length; ++i) {
      range[i] = i+start;
    }
    return range;
  }


  // CASTS ----------------------------------------------------------------------

  public static float[] doubleArrayToFloatArray(double[] a) {
    float[] result = new float[a.length];
    for (int i = 0; i < a.length; i++) {
      result[i] = (float) a[i];
    }
    return result;
  }

  public static double[] floatArrayToDoubleArray(float[] a) {
    double[] result = new double[a.length];
    for (int i = 0; i < a.length; i++) {
      result[i] = a[i];
    }
    return result;
  }

  public static double[][] floatArrayToDoubleArray(float[][] a) {
    double[][] result = new double[a.length][];
    for (int i = 0; i < a.length; i++) {
      float[] a_i = a[i];
      double[] row = result[i] = new double[a_i.length];
      for (int j = 0; j < a_i.length; j++) {
        row[j] = a_i[j];
      }
    }
    return result;
  }

  public static float[][] doubleArrayToFloatArray(double[][] a) {
    float[][] result = new float[a.length][];
    for (int i = 0; i < a.length; i++) {
      double[] a_i = a[i];
      float[] row = result[i] = new float[a_i.length];
      for (int j = 0; j < a_i.length; j++) {
        row[j] = (float) a_i[j];
      }
    }
    return result;
  }

  // OPERATIONS ON AN ARRAY - NONDESTRUCTIVE

  public static double[] exp(double[] a) {
    double[] result = new double[a.length];
    for (int i = 0; i < a.length; i++) {
      result[i] = Math.exp(a[i]);
    }
    return result;
  }

  public static double[] log(double[] a) {
    double[] result = new double[a.length];
    for (int i = 0; i < a.length; i++) {
      result[i] = Math.log(a[i]);
    }
    return result;
  }

  // OPERATIONS ON AN ARRAY - DESTRUCTIVE

  public static void expInPlace(double[] a) {
    for (int i = 0; i < a.length; i++) {
      a[i] = Math.exp(a[i]);
    }
  }

  public static void logInPlace(double[] a) {
    for (int i = 0; i < a.length; i++) {
      a[i] = Math.log(a[i]);
    }
  }

  public static double[] softmax(double[] scales) {
    double[] newScales = new double[scales.length];
    double sum = 0;
    for (int i = 0; i < scales.length; i++) {
      sum += newScales[i] = Math.exp(scales[i]);
    }
    for (int i = 0; i < scales.length; i++) {
      newScales[i] /= sum;
    }
    return newScales;
  }

  // OPERATIONS WITH SCALAR - DESTRUCTIVE

  /**
   * Increases the values in the first array a by b. Does it in place.
   *
   * @param a The array
   * @param b The amount by which to increase each item
   */
  public static void addInPlace(double[] a, double b) {
    for (int i = 0; i < a.length; i++) {
      a[i] += b;
    }
  }

  /**
   * Increases the values in this array by b. Does it in place.
   *
   * @param a The array
   * @param b The amount by which to increase each item
   */
  public static void addInPlace(float[] a, double b) {
    for (int i = 0; i < a.length; i++) {
      a[i] = (float) (a[i] + b);
    }
  }

  /**
   * Add c times the array b to array a. Does it in place.
   */
  public static void addMultInPlace(double[] a, double[] b, double c) {
    for (int i=0; i<a.length; i++) {
      a[i] += b[i] * c;
    }
  }

  /**
   * Scales the values in this array by b. Does it in place.
   */
  public static void multiplyInPlace(double[] a, double b) {
    for (int i = 0; i < a.length; i++) {
      a[i] *= b;
    }
  }

  /**
   * Scales the values in this array by b. Does it in place.
   */
  public static void multiplyInPlace(float[] a, double b) {
    for (int i = 0; i < a.length; i++) {
      a[i] *= b;
    }
  }

  /**
   * Divides the values in this array by b. Does it in place.
   */
  public static void divideInPlace(double[] a, double b) {
    for (int i = 0; i < a.length; i++) {
      a[i] /= b;
    }
  }


  /**
   * Scales the values in this array by c.
   */
  public static void powInPlace(double[] a, double c) {
    for (int i = 0; i < a.length; i++) {
      a[i] = Math.pow(a[i], c);
    }
  }

  /**
   * Sets the values in this array by to their value taken to cth power.
   */
  public static void powInPlace(float[] a, float c) {
    for (int i = 0; i < a.length; i++) {
      a[i] = (float) Math.pow(a[i], c);
    }
  }

  // OPERATIONS WITH SCALAR - NONDESTRUCTIVE

  public static double[] add(double[] a, double c) {
    double[] result = new double[a.length];
    for (int i = 0; i < a.length; i++) {
      result[i] = a[i] + c;
    }
    return result;
  }

  public static float[] add(float[] a, double c) {
    float[] result = new float[a.length];
    for (int i = 0; i < a.length; i++) {
      result[i] = (float) (a[i] + c);
    }
    return result;
  }

  /**
   * Scales the values in this array by c.
   */
  public static double[] multiply(double[] a, double c) {
    double[] result = new double[a.length];
    for (int i = 0; i < a.length; i++) {
      result[i] = a[i] * c;
    }
    return result;
  }

  /**
   * Scales the values in this array by c.
   */
  public static float[] multiply(float[] a, float c) {
    float[] result = new float[a.length];
    for (int i = 0; i < a.length; i++) {
      result[i] = a[i] * c;
    }
    return result;
  }

  /**
   * raises each entry in array a by power c
   */
  public static double[] pow(double[] a, double c) {
    double[] result = new double[a.length];
    for (int i = 0; i < a.length; i++) {
      result[i] = Math.pow(a[i], c);
    }
    return result;
  }

  /**
   * raises each entry in array a by power c
   */
  public static float[] pow(float[] a, float c) {
    float[] result = new float[a.length];
    for (int i = 0; i < a.length; i++) {
      result[i] = (float) Math.pow(a[i], c);
    }
    return result;
  }

  // OPERATIONS WITH TWO ARRAYS - DESTRUCTIVE

  public static void pairwiseAddInPlace(float[] to, float[] from) {
    if (to.length != from.length) {
      throw new IllegalArgumentException("to length:" + to.length + " from length:" + from.length);
    }
    for (int i = 0; i < to.length; i++) {
      to[i] += from[i];
    }
  }

  /**
   * Add the two 1d arrays in place of {@code to}.
   *
   * @throws java.lang.IllegalArgumentException If {@code to} and {@code from} are not of the same dimensions
   */
  public static void pairwiseAddInPlace(double[] to, double[] from) {
    if (to.length != from.length) {
      throw new IllegalArgumentException("to length:" + to.length + " from length:" + from.length);
    }
    for (int i = 0; i < to.length; i++) {
      to[i] += from[i];
    }
  }

  /**
   * Add the two 1d arrays in place of {@code to}.
   *
   * @throws java.lang.IllegalArgumentException If {@code to} and {@code from} are not of the same dimensions
   */
  public static void pairwiseAddInPlace(double[] to, float[] from) {
    if (to.length != from.length) {
      throw new IllegalArgumentException("to length:" + to.length + " from length:" + from.length);
    }
    for (int i = 0; i < to.length; i++) {
      to[i] += from[i];
    }
  }

  public static void pairwiseAddInPlace(double[] to, int[] from) {
    if (to.length != from.length) {
      throw new IllegalArgumentException();
    }
    for (int i = 0; i < to.length; i++) {
      to[i] += from[i];
    }
  }

  public static void pairwiseAddInPlace(double[] to, short[] from) {
    if (to.length != from.length) {
      throw new IllegalArgumentException();
    }
    for (int i = 0; i < to.length; i++) {
      to[i] += from[i];
    }
  }

  /**
   * Add the two 2d arrays and write the answer in place of {@code m1}.
   *
   * @throws IllegalArgumentException If {@code m1} and {@code m2} are not of the same dimensions
   */
  public static void addInPlace(double[][] m1, double[][] m2) {
    if (m1.length != m2.length) {
      throw new IllegalArgumentException();
    }
    for (int i = 0; i < m1.length; i++) {
      pairwiseAddInPlace(m1[i], m2[i]);
    }
  }


  public static void pairwiseSubtractInPlace(double[] to, double[] from) {
    if (to.length != from.length) {
      throw new ArrayIndexOutOfBoundsException("Arrays do not have the same length.");
    }
    for (int i = 0; i < to.length; i++) {
      to[i] -= from[i];
    }
  }

  public static void pairwiseScaleAddInPlace(double[] to, double[] from, double fromScale) {
    if (to.length != from.length) {
      throw new ArrayIndexOutOfBoundsException("Arrays do not have the same length.");
    }
    for (int i = 0; i < to.length; i++) {
      to[i] += fromScale * from[i];
    }
  }

  // OPERATIONS WITH TWO ARRAYS - NONDESTRUCTIVE

  public static int[] pairwiseAdd(int[] a, int[] b) {
    if (a.length != b.length) {
      throw new ArrayIndexOutOfBoundsException("Arrays do not have the same length.");
    }
    int[] result = new int[a.length];
    for (int i = 0; i < a.length; i++) {
      result[i] = a[i] + b[i];
    }
    return result;
  }

  public static double[] pairwiseAdd(double[] a, double[] b) {
    if (a.length != b.length) {
      throw new ArrayIndexOutOfBoundsException("Arrays do not have the same length.");
    }
    double[] result = new double[a.length];
    for (int i = 0; i < a.length; i++) {
      result[i] = a[i] + b[i];
    }
    return result;
  }

  public static float[] pairwiseAdd(float[] a, float[] b) {
    if (a.length != b.length) {
      throw new ArrayIndexOutOfBoundsException("Arrays do not have the same length.");
    }
    float[] result = new float[a.length];
    for (int i = 0; i < a.length; i++) {
      result[i] = a[i] + b[i];
    }
    return result;
  }

  public static double[] pairwiseScaleAdd(double[] a, double[] b, double bScale) {
    if (a.length != b.length) {
      throw new ArrayIndexOutOfBoundsException("Arrays do not have the same length.");
    }
    double[] result = new double[a.length];
    for (int i = 0; i < a.length; i++) {
      result[i] = a[i] + bScale * b[i];
    }
    return result;
  }

  public static double[] pairwiseSubtract(double[] a, double[] b) {
    if (a.length != b.length) {
      throw new ArrayIndexOutOfBoundsException("Arrays do not have the same length.");
    }
    double[] c = new double[a.length];
    for (int i = 0; i < a.length; i++) {
      c[i] = a[i] - b[i];
    }
    return c;
  }

  public static float[] pairwiseSubtract(float[] a, float[] b) {
    if (a.length != b.length) {
      throw new ArrayIndexOutOfBoundsException("Arrays do not have the same length.");
    }
    float[] c = new float[a.length];
    for (int i = 0; i < a.length; i++) {
      c[i] = a[i] - b[i];
    }
    return c;
  }

  /**
   * Assumes that both arrays have same length.
   */
  public static double dotProduct(double[] a, double[] b) {
    if (a.length != b.length) {
      throw new ArrayIndexOutOfBoundsException("Arrays do not have the same length.");
    }
    double result = 0;
    for (int i = 0; i < a.length; i++) {
      result += a[i] * b[i];
    }
    return result;
  }

  /**
   * Dot product of a and b.
   * <br>
   * Assumes that both arrays have same length.  Sum is done using a double.
   *
   * @param a float vector
   * @param b float vector
   * @return a dot b
   */
  public static float dotProduct(float[] a, float[] b) {
    if (a.length != b.length) {
      throw new ArrayIndexOutOfBoundsException("Arrays do not have the same length.");
    }
    double result = 0;
    for (int i = 0; i < a.length; i++) {
      result += a[i] * b[i];
    }
    return (float) result;
  }

  /**
   * Dot product of a and b.
   * <br>
   * Assumes that both arrays have same length.  Sum is done using a double.
   *
   * @param a double vector
   * @param b float vector
   * @return a dot b
   */
  public static double dotProduct(double[] a, float[] b) {
    if (a.length != b.length) {
      throw new ArrayIndexOutOfBoundsException("Arrays do not have the same length.");
    }
    double result = 0;
    for (int i = 0; i < a.length; i++) {
      result += a[i] * b[i];
    }
    return result;
  }

  /**
   * Assumes that both arrays have same length.
   */
  public static double[] pairwiseMultiply(double[] a, double[] b) {
    if (a.length != b.length) {
      throw new ArrayIndexOutOfBoundsException("Arrays do not have the same length.");
    }
    double[] result = new double[a.length];
    for (int i = 0; i < result.length; i++) {
      result[i] = a[i] * b[i];
    }
    return result;
  }

  /**
   * Assumes that both arrays have same length.
   */
  public static float[] pairwiseMultiply(float[] a, float[] b) {
    if (a.length != b.length) {
      throw new ArrayIndexOutOfBoundsException("Arrays do not have the same length.");
    }
    float[] result = new float[a.length];
    for (int i = 0; i < result.length; i++) {
      result[i] = a[i] * b[i];
    }
    return result;
  }

  /**
   * Puts the result in the result array.
   * Assumes that all arrays have same length.
   */
  public static void pairwiseMultiply(double[] a, double[] b, double[] result) {
    if (a.length != b.length) {
      throw new ArrayIndexOutOfBoundsException("Arrays do not have the same length.");
    }
    for (int i = 0; i < result.length; i++) {
      result[i] = a[i] * b[i];
    }
  }

  /**
   * Puts the result in the result array.
   * Assumes that all arrays have same length.
   */
  public static void pairwiseMultiply(float[] a, float[] b, float[] result) {
    if (a.length != b.length) {
      throw new ArrayIndexOutOfBoundsException("Arrays do not have the same length.");
    }
    for (int i = 0; i < result.length; i++) {
      result[i] = a[i] * b[i];
    }
  }

  /**
   * Divide the first array by the second elementwise,
   * and store results in place. Assume arrays have
   * the same length
   */
  public static void pairwiseDivideInPlace(double[] a, double[] b) {
    if (a.length != b.length) {
      throw new ArrayIndexOutOfBoundsException("Arrays do not have the same length.");
    }
    for (int i = 0; i < a.length; i++) {
      a[i] /= b[i];
    }
  }

  // ERROR CHECKING

  public static boolean hasNaN(double[] a) {
    for (double x : a) {
      if (Double.isNaN(x)) return true;
    }
    return false;
  }

  public static boolean hasInfinite(double[] a) {
    for (double anA : a) {
      if (Double.isInfinite(anA)) return true;
    }
    return false;
  }

  public static boolean hasNaN(float[] a) {
    for (float x : a) {
      if (Float.isNaN(x)) return true;
    }
    return false;
  }

  // methods for filtering vectors ------------------------------------------

  public static int countNaN(double[] v) {
    int c = 0;
    for (double d : v) {
      if (Double.isNaN(d)) {
        c++;
      }
    }
    return c;
  }

  public static double[] filterNaN(double[] v) {
    double[] u = new double[numRows(v) - countNaN(v)];
    int j = 0;
    for (double d : v) {
      if ( ! Double.isNaN(d)) {
        u[j++] = d;
      }
    }
    return u;
  }

  public static int countInfinite(double[] v) {
    int c = 0;
    for (double aV : v)
      if (Double.isInfinite(aV))
        c++;
    return c;
  }

  public static int countNonZero(double[] v) {
    int c = 0;
    for (double aV : v)
      if (aV != 0.0)
        ++c;
    return c;
  }

  public static int countCloseToZero(double[] v, double epsilon) {
    int c = 0;
    for (double aV : v)
      if (Math.abs(aV) < epsilon)
        ++c;
    return c;
  }

  public static int countPositive(double[] v) {
    int c = 0;
    for (double a : v) {
      if (a > 0.0) {
        ++c;
      }
    }
    return c;
  }

  public static int countNegative(double[] v) {
    int c = 0;
    for (double aV : v)
      if (aV < 0.0)
        ++c;
    return c;
  }

  public static double[] filterInfinite(double[] v) {
    double[] u = new double[numRows(v) - countInfinite(v)];
    int j = 0;
    for (double aV : v) {
      if (!Double.isInfinite(aV)) {
        u[j++] = aV;
      }
    }
    return u;
  }

  public static double[] filterNaNAndInfinite(double[] v) {
    return filterInfinite(filterNaN(v));
  }


  // VECTOR PROPERTIES

  /**
   * Returns the sum of an array of doubles.
   */
  public static double sum(double[] a) {
    return sum(a,0,a.length);
  }

  /**
   * Returns the sum of the portion of an array of numbers between
   * {@code fromIndex}, inclusive, and {@code toIndex}, exclusive.
   * Returns 0 if {@code fromIndex >= toIndex}.
   */
  public static double sum(double[] a, int fromIndex, int toIndex) {
    double result = 0.0;
    for (int i = fromIndex; i < toIndex; i++) {
      result += a[i];
    }
    return result;
  }


  public static int sum(int[] a) {
    int result = 0;
    for (int i : a) {
      result += i;
    }
    return result;
  }

  public static float sum(float[] a) {
    float result = 0.0F;
    for (float f : a) {
      result += f;
    }
    return result;
  }

  public static int sum(int[][] a) {
    int result = 0;
    for (int[] v : a) {
      for (int item : v) {
        result += item;
      }
    }
    return result;
  }

  /**
   * Returns diagonal elements of the given (square) matrix.
   */
  public static int[] diag(int[][] a) {
    int[] rv = new int[a.length];
    for (int i = 0; i < a.length; i++) {
      rv[i] = a[i][i];
    }
    return rv;
  }

  public static double average(double[] a) {
    return ArrayMath.sum(a) / a.length;
  }

  /** This version avoids certain types of overflow. */
  public static double iterativeAverage(double[] a) {
    double avg = 0.0;
    int t = 0;
    for (double x : a) {
      avg += (x - avg) / ++t;
    }
    return avg;
  }

  /**
   * Computes inf-norm of vector.
   * This is just the largest absolute value of an element.
   *
   * @param a Array of double
   * @return inf-norm of a
   */
  public static double norm_inf(double[] a) {
    double max = Double.NEGATIVE_INFINITY;
    for (double d : a) {
      if (Math.abs(d) > max) {
        max = Math.abs(d);
      }
    }
    return max;
  }


  /**
   * Computes inf-norm of vector.
   *
   * @return inf-norm of a
   */
  public static double norm_inf(float[] a) {
    double max = Double.NEGATIVE_INFINITY;
    for (float anA : a) {
      if (Math.abs(anA) > max) {
        max = Math.abs(anA);
      }
    }
    return max;
  }

  /**
   * Computes 1-norm of vector.
   *
   * @param a A vector of double
   * @return 1-norm of a
   */
  public static double norm_1(double[] a) {
    double sum = 0.0;
    for (double anA : a) {
      sum += anA < 0 ? -anA : anA;
    }
    return sum;
  }

  /**
   * Computes 1-norm of vector.
   *
   * @param a A vector of floats
   * @return 1-norm of a
   */
  public static double norm_1(float[] a) {
    double sum = 0.0;
    for (float anA : a) {
      sum += anA < 0 ? -anA : anA;
    }
    return sum;
  }


  /**
   * Computes 2-norm of vector.
   *
   * @param a A vector of double
   * @return Euclidean norm of a
   */
  public static double norm(double[] a) {
    double squaredSum = 0.0;
    for (double anA : a) {
      squaredSum += anA * anA;
    }
    return Math.sqrt(squaredSum);
  }

  /**
   * Computes 2-norm of vector.
   *
   * @param a A vector of floats
   * @return Euclidean norm of a
   */
  public static double norm(float[] a) {
    double squaredSum = 0.0;
    for (float anA : a) {
      squaredSum += anA * anA;
    }
    return Math.sqrt(squaredSum);
  }

  /**
   * @return the index of the max value; if max is a tie, returns the first one.
   */
  public static int argmax(double[] a) {
    double max = Double.NEGATIVE_INFINITY;
    int argmax = 0;
    for (int i = 0; i < a.length; i++) {
      double d = a[i];
      if (d > max) {
        max = d;
        argmax = i;
      }
    }
    return argmax;
  }

  /**
   * @return the index of the max value; if max is a tie, returns the last one.
   */
  public static int argmax_tieLast(double[] a) {
    double max = Double.NEGATIVE_INFINITY;
    int argmax = 0;
    for (int i = 0; i < a.length; i++) {
      double d = a[i];
      if (d >= max) {
        max = d;
        argmax = i;
      }
    }
    return argmax;
  }

  public static double max(double[] a) {
    return a[argmax(a)];
  }

  public static double max(Collection<Double> a) {
    double max = Double.NEGATIVE_INFINITY;
    for (double d : a) {
      if (d > max) { max = d; }
    }
    return max;
  }

  /**
   * @return the index of the max value; if max is a tie, returns the first one.
   */
  public static int argmax(float[] a) {
    float max = Float.NEGATIVE_INFINITY;
    int argmax = 0;
    for (int i = 0; i < a.length; i++) {
      float d = a[i];
      if (d > max) {
        max = d;
        argmax = i;
      }
    }
    return argmax;
  }

  public static float max(float[] a) {
    return a[argmax(a)];
  }

  /**
   * @return the index of the min value; if min is a tie, returns the lowest index one.
   */
  public static int argmin(double[] a) {
    double min = Double.POSITIVE_INFINITY;
    int argmin = 0;
    for (int i = 0; i < a.length; i++) {
      double d = a[i];
      if (d < min) {
        min = d;
        argmin = i;
      }
    }
    return argmin;
  }

  /**
   * @return The minimum value in an array.
   */
  public static double min(double... vector) {
    double min = Double.POSITIVE_INFINITY;
    for (double x : vector) {
      if (x < min) {
        min = x;
      }
    }
    return min;
  }

  /**
   * Returns the smallest value in a vector of doubles.  Any values which
   * are NaN or infinite are ignored.  If the vector is empty, 0.0 is
   * returned.
   */
  public static double safeMin(double[] v) {
    double[] u = filterNaNAndInfinite(v);
    return numRows(u) == 0 ? 0.0 : min(u);
  }

  /**
   * @return the index of the min value; if min is a tie, returns the first one.
   */
  public static int argmin(float[] a) {
    float min = Float.POSITIVE_INFINITY;
    int argmin = 0;
    for (int i = 0; i < a.length; i++) {
      float d = a[i];
      if (d < min) {
        min = d;
        argmin = i;
      }
    }
    return argmin;
  }

  public static float min(float[] a) {
    return a[argmin(a)];
  }

  /**
   * @return the index of the min value; if min is a tie, returns the first one.
   */
  public static int argmin(int[] a) {
    int min = Integer.MAX_VALUE;
    int argmin = 0;
    for (int i = 0; i < a.length; i++) {
      int d = a[i];
      if (d < min) {
        min = d;
        argmin = i;
      }
    }
    return argmin;
  }

  /**
   * @return the min value.
   */
  public static int min(int... vector) {
    int min = Integer.MAX_VALUE;
    for (int x : vector) {
      if (x < min) {
        min = x;
      }
    }
    return min;
  }


  /**
   * @return the index of the max value; if max is a tie, returns the first one.
   */
  public static int argmax(int[] a) {
    int max = Integer.MIN_VALUE;
    int argmax = 0;
    for (int i = 0; i < a.length; i++) {
      int d = a[i];
      if (d > max) {
        max = d;
        argmax = i;
      }
    }
    return argmax;
  }

  /**
   * @return the index of the max value; if max is a tie, returns the first one.
   */
  public static int max(int... vector) {
    int max = Integer.MIN_VALUE;
    for (int x : vector) {
      if (x > max) {
        max = x;
      }
    }
    return max;
  }


  /** Returns the smallest element of the matrix */
  public static int min(int[][] matrix) {
    int min = Integer.MAX_VALUE;
    for (int[] row : matrix) {
      for (int elem : row) {
        min = Math.min(min, elem);
      }
    }
    return min;
  }

  /** Returns the smallest element of the matrix */
  public static int max(int[][] matrix) {
    int max = Integer.MIN_VALUE;
    for (int[] row : matrix) {
      for (int elem : row) {
        max = Math.max(max, elem);
      }
    }
    return max;
  }

  /**
   * Returns the largest value in a vector of doubles.  Any values which
   * are NaN or infinite are ignored.  If the vector is empty, 0.0 is
   * returned.
   */
  public static double safeMax(double[] v) {
    double[] u = filterNaNAndInfinite(v);
    return numRows(u) == 0 ? 0.0 : max(u);
  }

  /**
   * Returns the log of the sum of an array of numbers, which are
   * themselves input in log form.  This is all natural logarithms.
   * Reasonable care is taken to do this as efficiently as possible
   * (under the assumption that the numbers might differ greatly in
   * magnitude), with high accuracy, and without numerical overflow.
   *
   * @param logInputs An array of numbers [log(x1), ..., log(xn)]
   * @return {@literal log(x1 + ... + xn)}
   */
  public static double logSum(double... logInputs) {
    return logSum(logInputs,0,logInputs.length);
  }

  /**
   * Returns the log of the portion between {@code fromIndex}, inclusive, and
   * {@code toIndex}, exclusive, of an array of numbers, which are
   * themselves input in log form.  This is all natural logarithms.
   * Reasonable care is taken to do this as efficiently as possible
   * (under the assumption that the numbers might differ greatly in
   * magnitude), with high accuracy, and without numerical overflow.  Throws an
   * {@link IllegalArgumentException} if {@code logInputs} is of length zero.
   * Otherwise, returns Double.NEGATIVE_INFINITY if {@code fromIndex} &gt;=
   * {@code toIndex}.
   *
   * @param logInputs An array of numbers [log(x1), ..., log(xn)]
   * @param fromIndex The array index to start the sum from
   * @param toIndex The array index after the last element to be summed
   * @return {@literal log(x1 + ... + xn)}
   */
  public static double logSum(double[] logInputs, int fromIndex, int toIndex) {
    if (Thread.interrupted()) {  // A good place to check for interrupts -- many functions call this
      throw new RuntimeInterruptedException();
    }
    if (logInputs.length == 0)
      throw new IllegalArgumentException();
    if(fromIndex >= 0 && toIndex < logInputs.length && fromIndex >= toIndex)
      return Double.NEGATIVE_INFINITY;
    int maxIdx = fromIndex;
    double max = logInputs[fromIndex];
    for (int i = fromIndex+1; i < toIndex; i++) {
      double d = logInputs[i];
      if (d > max) {
        maxIdx = i;
        max = d;
      }
    }
    double intermediate = 0.0;
    double cutoff = max - SloppyMath.LOGTOLERANCE;
    // we avoid rearranging the array and so test indices each time!
    for (int i = fromIndex; i < toIndex; i++) {
      double d = logInputs[i];
      if (i != maxIdx && d > cutoff) {
        intermediate += Math.exp(d - max);
      }
    }
    return intermediate != 0 ? (max + Math.log(1 + intermediate)) : max;
  }

  /**
   * Returns the log of the portion between {@code fromIndex}, inclusive, and
   * {@code toIndex}, exclusive, of an array of numbers, which are
   * themselves input in log form.  This is all natural logarithms.
   * This version incorporates a stride, so you can sum only select numbers.
   * Reasonable care is taken to do this as efficiently as possible
   * (under the assumption that the numbers might differ greatly in
   * magnitude), with high accuracy, and without numerical overflow.  Throws an
   * {@link IllegalArgumentException} if {@code logInputs} is of length zero.
   * Otherwise, returns Double.NEGATIVE_INFINITY if {@code fromIndex} &gt;=
   * {@code toIndex}.
   *
   * @param logInputs An array of numbers [log(x1), ..., log(xn)]
   * @param fromIndex The array index to start the sum from
   * @param afterIndex The array index after the last element to be summed
   * @return {@literal log(x1 + ... + xn)}
   */
  public static double logSum(double[] logInputs, int fromIndex, int afterIndex, int stride) {
    if (logInputs.length == 0)
      throw new IllegalArgumentException();
    if (fromIndex >= 0 && afterIndex < logInputs.length && fromIndex >= afterIndex)
      return Double.NEGATIVE_INFINITY;
    int maxIdx = fromIndex;
    double max = logInputs[fromIndex];
    for (int i = fromIndex + stride; i < afterIndex; i += stride) {
      double d = logInputs[i];
      if (d > max) {
        maxIdx = i;
        max = d;
      }
    }
    double intermediate = 0.0;
    double cutoff = max - SloppyMath.LOGTOLERANCE;
    // we avoid rearranging the array and so test indices each time!
    for (int i = fromIndex; i < afterIndex; i += stride) {
      double d = logInputs[i];
      if (i != maxIdx && d > cutoff) {
        intermediate += Math.exp(d - max);
      }
    }
    return intermediate != 0 ? max + Math.log(1.0 + intermediate) : max;
  }

  public static double logSum(List<Double> logInputs) {
    return logSum(logInputs, 0, logInputs.size());
  }

  public static double logSum(List<Double> logInputs, int fromIndex, int toIndex) {
    int length = logInputs.size();
    if (length == 0)
      throw new IllegalArgumentException();
    if(fromIndex >= 0 && toIndex < length && fromIndex >= toIndex)
      return Double.NEGATIVE_INFINITY;
    int maxIdx = fromIndex;
    double max = logInputs.get(fromIndex);
    for (int i = fromIndex+1; i < toIndex; i++) {
      double d = logInputs.get(i);
      if (d > max) {
        maxIdx = i;
        max = d;
      }
    }
    double intermediate = 0.0;
    double cutoff = max - SloppyMath.LOGTOLERANCE;
    // we avoid rearranging the array and so test indices each time!
    for (int i = fromIndex; i < toIndex; i++) {
      double d = logInputs.get(i);
      if (i != maxIdx && d > cutoff) {
        intermediate += Math.exp(d - max);
      }
    }
    return intermediate != 0. ? max + Math.log(1.0 + intermediate) : max;
  }


  /**
   * Returns the log of the sum of an array of numbers, which are
   * themselves input in log form.  This is all natural logarithms.
   * Reasonable care is taken to do this as efficiently as possible
   * (under the assumption that the numbers might differ greatly in
   * magnitude), with high accuracy, and without numerical overflow.
   *
   * @param logInputs An array of numbers [log(x1), ..., log(xn)]
   * @return {@literal log(x1 + ... + xn)}
   */
  public static float logSum(float[] logInputs) {
    int leng = logInputs.length;
    if (leng == 0) {
      throw new IllegalArgumentException();
    }
    int maxIdx = 0;
    float max = logInputs[0];
    for (int i = 1; i < leng; i++) {
      float d = logInputs[i];
      if (d > max) {
        maxIdx = i;
        max = d;
      }
    }
    double intermediate = 0.0f;
    float cutoff = max - SloppyMath.LOGTOLERANCE_F;
    // we avoid rearranging the array and so test indices each time!
    for (int i = 0; i < leng; i++) {
      if (i != maxIdx && logInputs[i] > cutoff) {
        intermediate += Math.exp(logInputs[i] - max);
      }
    }
    return intermediate != 0. ? max + (float) Math.log(1.0 + intermediate) : max;
  }

  // LINEAR ALGEBRAIC FUNCTIONS

  public static double innerProduct(double[] a, double[] b) {
    double result = 0.0;
    int len = Math.min(a.length, b.length);
    for (int i = 0; i < len; i++) {
      result += a[i] * b[i];
    }
    return result;
  }

  public static double innerProduct(float[] a, float[] b) {
    double result = 0.0;
    int len = Math.min(a.length, b.length);
    for (int i = 0; i < len; i++) {
      result += a[i] * b[i];
    }
    return result;
  }

  // UTILITIES

  public static double[][] load2DMatrixFromFile(String filename) throws IOException {
    String s = IOUtils.slurpFile(filename);
    String[] rows = s.split("[\r\n]+");
    double[][] result = new double[rows.length][];
    for (int i=0; i<result.length; i++) {
      String[] columns = rows[i].split("\\s+");
      result[i] = new double[columns.length];
      for (int j=0; j<result[i].length; j++) {
        result[i][j] = Double.parseDouble(columns[j]);
      }
    }
    return result;
  }

  public static Integer[] box(int[] assignment) {
    Integer[] result = new Integer[assignment.length];
    for (int i=0; i<assignment.length; i++) {
      result[i] = Integer.valueOf(assignment[i]);
    }
    return result;
  }

  public static int[] unboxToInt(Collection<Integer> list) {
    int[] result = new int[list.size()];
    int i = 0;
    for (int v : list) {
      result[i++] = v;
    }
    return result;
  }

  public static Double[] box(double[] assignment) {
    Double[] result = new Double[assignment.length];
    for (int i=0; i<assignment.length; i++) {
      result[i] = Double.valueOf(assignment[i]);
    }
    return result;
  }

  public static double[] unbox(Collection<Double> list) {
    double[] result = new double[list.size()];
    int i = 0;
    for (double v : list) {
      result[i++] = v;
    }
    return result;
  }

  public static int indexOf(int n, int[] a) {
    for (int i=0; i<a.length; i++) {
      if (a[i]==n) return i;
    }
    return -1;
  }

  public static int[][] castToInt(double[][] doubleCounts) {
    int[][] result = new int[doubleCounts.length][];
    for (int i=0; i<doubleCounts.length; i++) {
      double[] drow = doubleCounts[i];
      int[] row = result[i] = new int[drow.length];
      for (int j=0; j<drow.length; j++) {
        row[j] = (int) drow[j];
      }
    }
    return result;
  }

  // PROBABILITY FUNCTIONS

  /**
   * Makes the values in this array sum to 1.0. Does it in place.
   * If the total is 0.0 or NaN, throws an RuntimeException.
   */
  public static void normalize(double[] a) {
    double total = sum(a);
    if (total == 0.0 || Double.isNaN(total)) {
      throw new ArithmeticException("Can't normalize an array with sum 0.0 or NaN: " + Arrays.toString(a));
    }
    multiplyInPlace(a, 1.0/total); // divide each value by total
  }

  public static void L1normalize(double[] a) {
    double total = L1Norm(a);
    if (total == 0.0 || Double.isNaN(total))
      if (a.length < 100) {
        throw new ArithmeticException("Can't normalize an array with sum 0.0 or NaN: " + Arrays.toString(a));
      } else {
        throw new ArithmeticException("Can't normalize an array with sum 0.0 or NaN: " + Arrays.toString(Arrays.copyOf(a, 100)) + " ... ");
      }
    multiplyInPlace(a, 1.0/total); // divide each value by total
  }
  public static void L2normalize(double[] a) {
    double total = L2Norm(a);
    if (total == 0.0 || Double.isNaN(total)) {
      if (a.length < 100) {
        throw new ArithmeticException("Can't normalize an array with sum 0.0 or NaN: " + Arrays.toString(a));
      } else {
        throw new ArithmeticException("Can't normalize an array with sum 0.0 or NaN: " + Arrays.toString(Arrays.copyOf(a, 100)) + " ... ");
      }
    }
    multiplyInPlace(a, 1.0/total); // divide each value by total
  }

  /**
   * Makes the values in this array sum to 1.0. Does it in place.
   * If the total is 0.0 or NaN, throws an RuntimeException.
   */
  public static void normalize(float[] a) {
    float total = sum(a);
    if (total == 0.0f || Double.isNaN(total)) {
      throw new ArithmeticException("Can't normalize an array with sum 0.0 or NaN");
    }
    multiplyInPlace(a, 1.0f/total); // divide each value by total
  }
  public static void L2normalize(float[] a) {
    float total = L2Norm(a);
    if (total == 0.0 || Float.isNaN(total)) {
      if (a.length < 100) {
        throw new ArithmeticException("Can't normalize an array with sum 0.0 or NaN: " + Arrays.toString(a));
      } else {
        throw new ArithmeticException("Can't normalize an array with sum 0.0 or NaN: " + Arrays.toString(Arrays.copyOf(a, 100)) + " ... ");
      }

    }
    multiplyInPlace(a, 1.0/total); // divide each value by total
  }

  /**
   * Standardize values in this array, i.e., subtract the mean and divide by the standard deviation.
   * If standard deviation is 0.0, throws a RuntimeException.
   */
  public static void standardize(double[] a) {
    double m = mean(a);
    if (Double.isNaN(m)) {
      throw new ArithmeticException("Can't standardize array whose mean is NaN");
    }
    double s = stdev(a);
    if (s == 0.0 || Double.isNaN(s)) {
      throw new ArithmeticException("Can't standardize array whose standard deviation is 0.0 or NaN");
    }
    addInPlace(a, -m); // subtract mean
    multiplyInPlace(a, 1.0/s); // divide by standard deviation
  }

  public static double L2Norm(double[] a) {
    double result = 0.0;
    for(double d: a) {
      result += d * d;
    }
    return Math.sqrt(result);
  }
  public static float L2Norm(float[] a) {
    double result = 0;
    for(float d: a) {
      result += d * d;
    }
    return (float) Math.sqrt(result);
  }

  public static double L1Norm(double[] a) {
    double result = 0.0;
    for (double d: a) {
      result += Math.abs(d);
    }
    return result;
  }

  /**
   * Makes the values in this array sum to 1.0. Does it in place.
   * If the total is 0.0, throws a RuntimeException.
   * If the total is Double.NEGATIVE_INFINITY, then it replaces the
   * array with a normalized uniform distribution. CDM: This last bit is
   * weird!  Do we really want that?
   */
  public static void logNormalize(double[] a) {
    double logTotal = logSum(a);
    if (logTotal == Double.NEGATIVE_INFINITY) {
      // to avoid NaN values
      double v = -Math.log(a.length);
      for (int i = 0; i < a.length; i++) {
        a[i] = v;
      }
      return;
    }
    addInPlace(a, -logTotal); // subtract log total from each value
  }

  /**
   * Samples from the distribution over values 0 through d.length given by d.
   * Assumes that the distribution sums to 1.0.
   *
   * @param d the distribution to sample from
   * @return a value from 0 to d.length
   */
  public static int sampleFromDistribution(double[] d) {
    return sampleFromDistribution(d, rand);
  }

  /**
   * Samples from the distribution over values 0 through d.length given by d.
   * Assumes that the distribution sums to 1.0.
   *
   * @param d the distribution to sample from
   * @return a value from 0 to d.length
   */
  public static int sampleFromDistribution(double[] d, Random random) {
    // sample from the uniform [0,1]
    double r = random.nextDouble();
    // now compare its value to cumulative values to find what interval it falls in
    double total = 0;
    for (int i = 0; i < d.length - 1; i++) {
      if (Double.isNaN(d[i])) {
        throw new RuntimeException("Can't sample from NaN");
      }
      total += d[i];
      if (r < total) {
        return i;
      }
    }
    return d.length - 1; // in case the "double-math" didn't total to exactly 1.0
  }

  /**
   * Samples from the distribution over values 0 through d.length given by d.
   * Assumes that the distribution sums to 1.0.
   *
   * @param d the distribution to sample from
   * @return a value from 0 to d.length
   */
  public static int sampleFromDistribution(float[] d, Random random) {
    // sample from the uniform [0,1]
    double r = random.nextDouble();
    // now compare its value to cumulative values to find what interval it falls in
    double total = 0;
    for (int i = 0; i < d.length - 1; i++) {
      if (Float.isNaN(d[i])) {
        throw new RuntimeException("Can't sample from NaN");
      }
      total += d[i];
      if (r < total) {
        return i;
      }
    }
    return d.length - 1; // in case the "double-math" didn't total to exactly 1.0
  }

  /** we will multiply by this constant instead of divide by log(2) */
  private static final double LN_TO_LOG2 = 1. / Math.log(2);

  public static double klDivergence(double[] from, double[] to) {
    double kl = 0.0;
    double tot = sum(from);
    double tot2 = sum(to);
    // System.out.println("tot is " + tot + " tot2 is " + tot2);
    for (int i = 0; i < from.length; i++) {
      if (from[i] == 0.0) {
        continue;
      }
      double num = from[i] / tot;
      double num2 = to[i] / tot2;
      // System.out.println("num is " + num + " num2 is " + num2);
      kl += num * Math.log(num / num2) * LN_TO_LOG2;
    }
    return kl;
  }

  /**
   * Returns the Jensen Shannon divergence (information radius) between
   * a and b, defined as the average of the kl divergences from a to b
   * and from b to a.
   */
  public static double jensenShannonDivergence(double[] a, double[] b) {
    double[] average = pairwiseAdd(a, b);
    multiplyInPlace(average, .5);
    return .5 * klDivergence(a, average) + .5 * klDivergence(b, average);
  }

  public static void setToLogDeterministic(float[] a, int i) {
    Arrays.fill(a, Float.NEGATIVE_INFINITY);
    a[i] = 0.f;
  }

  public static void setToLogDeterministic(double[] a, int i) {
    Arrays.fill(a, Double.NEGATIVE_INFINITY);
    a[i] = 0.;
  }

  // SAMPLE ANALYSIS

  public static double mean(double[] a) {
    return sum(a) / a.length;
  }

  /** Return the mean of an array of int. */
  public static double mean(int[] a) {
    return ((double) sum(a)) / a.length;
  }

  public static double median(double[] a) {
    double[] b = a.clone();
    Arrays.sort(b);
    int mid = b.length >>> 1;
    return (b.length & 0x1) == 0 ? (b[mid - 1] + b[mid]) * 0.5 : b[mid];
  }

  /**
   * Returns the mean of a vector of doubles.  Any values which are NaN or
   * infinite are ignored.  If the vector is empty, 0.0 is returned.
   */
  public static double safeMean(double[] v) {
    double[] u = filterNaNAndInfinite(v);
    return numRows(u) == 0 ? 0 : mean(u);
  }

  public static double sumSquaredError(double[] a) {
    double mean = mean(a);
    double result = 0.0;
    for (double anA : a) {
      double diff = anA - mean;
      result += (diff * diff);
    }
    return result;
  }

  public static double sumSquared(double[] a) {
    double result = 0.0;
    for (double anA : a) {
      result += (anA * anA);
    }
    return result;
  }

  public static double variance(double[] a) {
    return sumSquaredError(a) / (a.length - 1);
  }

  public static double stdev(double[] a) {
    return Math.sqrt(variance(a));
  }

  /**
   * Returns the standard deviation of a vector of doubles.  Any values which
   * are NaN or infinite are ignored.  If the vector contains fewer than two
   * values, 1.0 is returned.
   */
  public static double safeStdev(double[] v) {
    double[] u = filterNaNAndInfinite(v);
    return numRows(u) < 2 ? 1.0 : stdev(u);
  }

  public static double standardErrorOfMean(double[] a) {
    return stdev(a) / Math.sqrt(a.length);
  }


  /**
   * Fills the array with sample from 0 to numArgClasses-1 without replacement.
   */
  public static void sampleWithoutReplacement(int[] array, int numArgClasses) {
    sampleWithoutReplacement(array, numArgClasses, rand);
  }
  /**
   * Fills the array with sample from 0 to numArgClasses-1 without replacement.
   */
  public static void sampleWithoutReplacement(int[] array, int numArgClasses, Random rand) {
    int[] temp = new int[numArgClasses];
    for (int i = 0; i < temp.length; i++) {
      temp[i] = i;
    }
    shuffle(temp, rand);
    System.arraycopy(temp, 0, array, 0, array.length);
  }

  public static void shuffle(int[] a) {
    shuffle(a, rand);
  }

  /* Shuffle the integers in an array using a source of randomness.
   * Uses the Fisher-Yates shuffle. Makes all orderings equally likely, iff
   * the randomizer is good.
   *
   * @param a The array to shuffle
   * @param rand The source of randomness
   */
  public static void shuffle(int[] a, Random rand) {
    for (int i = a.length - 1; i > 0; i--) {
      int j = rand.nextInt(i+1); // a random index from 0 to i inclusive, may shuffle with itself
      int tmp = a[i];
      a[i] = a[j];
      a[j] = tmp;
    }
  }

  public static void reverse(int[] a) {
    for (int i=0; i<a.length/2; i++) {
      int j = a.length - i - 1;
      int tmp = a[i];
      a[i] = a[j];
      a[j] = tmp;
    }
  }

  public static boolean contains(int[] a, int i) {
    for (int k : a) {
      if (k == i) return true;
    }
    return false;
  }

  public static boolean containsInSubarray(int[] a, int begin, int end, int i) {
    for (int j = begin; j < end; j++) {
      if (a[j]==i) return true;
    }
    return false;
  }

  /**
   * Direct computation of Pearson product-moment correlation coefficient.
   * Note that if x and y are involved in several computations of
   * pearsonCorrelation, it is perhaps more advisable to first standardize
   * x and y, then compute innerProduct(x,y)/(x.length-1).
   */
  public static double pearsonCorrelation(double[] x, double[] y) {
    double sum_sq_x = 0, sum_sq_y = 0;
    double mean_x = x[0], mean_y = y[0];
    double sum_coproduct = 0;
    for(int i=2; i<x.length+1;++i) {
      double w = (i - 1)*1.0/i;
      double delta_x = x[i-1] - mean_x;
      double delta_y = y[i-1] - mean_y;
      sum_sq_x += delta_x * delta_x*w;
      sum_sq_y += delta_y * delta_y*w;
      sum_coproduct += delta_x * delta_y*w;
      mean_x += delta_x / i;
      mean_y += delta_y / i;
    }
    double pop_sd_x = Math.sqrt(sum_sq_x/x.length);
    double pop_sd_y = Math.sqrt(sum_sq_y/y.length);
    double cov_x_y = sum_coproduct / x.length;
    double denom = pop_sd_x*pop_sd_y;
    return denom == 0. ? 0. : cov_x_y/denom;
  }

  /**
   * Computes the significance level by approximate randomization, using a
   * default value of 1000 iterations.  See documentation for other version
   * of method.
   */
  public static double sigLevelByApproxRand(double[] A, double[] B) {
    return sigLevelByApproxRand(A, B, 1000);
  }

  /**
   * Takes a pair of arrays, A and B, which represent corresponding
   * outcomes of a pair of random variables: say, results for two different
   * classifiers on a sequence of inputs.  Returns the estimated
   * probability that the difference between the means of A and B is not
   * significant, that is, the significance level.  This is computed by
   * "approximate randomization".  The test statistic is the absolute
   * difference between the means of the two arrays.  A randomized test
   * statistic is computed the same way after initially randomizing the
   * arrays by swapping each pair of elements with 50% probability.  For
   * the given number of iterations, we generate a randomized test
   * statistic and compare it to the actual test statistic.  The return
   * value is the proportion of iterations in which a randomized test
   * statistic was found to exceed the actual test statistic.
   *
   * @param A Outcome of one r.v.
   * @param B Outcome of another r.v.
   * @return Significance level by randomization
   */
  public static double sigLevelByApproxRand(double[] A, double[] B, int iterations) {
    if (A.length == 0)
      throw new IllegalArgumentException("Input arrays must not be empty!");
    if (A.length != B.length)
      throw new IllegalArgumentException("Input arrays must have equal length!");
    if (iterations <= 0)
      throw new IllegalArgumentException("Number of iterations must be positive!");
    double testStatistic = absDiffOfMeans(A, B, false); // not randomized
    int successes = 0;
    for (int i = 0; i < iterations; i++) {
      double t =  absDiffOfMeans(A, B, true); // randomized
      if (t >= testStatistic) successes++;
    }
    return (double) (successes + 1) / (double) (iterations + 1);
  }

  public static double sigLevelByApproxRand(int[] A, int[] B) {
    return sigLevelByApproxRand(A, B, 1000);
  }

  public static double sigLevelByApproxRand(int[] A, int[] B, int iterations) {
    if (A.length == 0)
      throw new IllegalArgumentException("Input arrays must not be empty!");
    if (A.length != B.length)
      throw new IllegalArgumentException("Input arrays must have equal length!");
    if (iterations <= 0)
      throw new IllegalArgumentException("Number of iterations must be positive!");
    double[] X = new double[A.length];
    double[] Y = new double[B.length];
    for (int i = 0; i < A.length; i++) {
      X[i] = A[i];
      Y[i] = B[i];
    }
    return sigLevelByApproxRand(X, Y, iterations);
  }

  public static double sigLevelByApproxRand(boolean[] A, boolean[] B) {
    return sigLevelByApproxRand(A, B, 1000);
  }

  public static double sigLevelByApproxRand(boolean[] A, boolean[] B, int iterations) {
    if (A.length == 0)
      throw new IllegalArgumentException("Input arrays must not be empty!");
    if (A.length != B.length)
      throw new IllegalArgumentException("Input arrays must have equal length!");
    if (iterations <= 0)
      throw new IllegalArgumentException("Number of iterations must be positive!");
    double[] X = new double[A.length];
    double[] Y = new double[B.length];
    for (int i = 0; i < A.length; i++) {
      X[i] = (A[i] ? 1.0 : 0.0);
      Y[i] = (B[i] ? 1.0 : 0.0);
    }
    return sigLevelByApproxRand(X, Y, iterations);
  }


  // Returns the absolute difference between the means of arrays A and B.
  // If 'randomize' is true, swaps matched A & B entries with 50% probability
  // Assumes input arrays have equal, non-zero length.
  private static double absDiffOfMeans(double[] A, double[] B, boolean randomize) {
    Random random = new Random();
    double aTotal = 0.0;
    double bTotal = 0.0;
    for (int i = 0; i < A.length; i++) {
      if (randomize && random.nextBoolean()) {
        aTotal += B[i];
        bTotal += A[i];
      } else {
        aTotal += A[i];
        bTotal += B[i];
      }
    }
    double aMean = aTotal / A.length;
    double bMean = bTotal / B.length;
    return Math.abs(aMean - bMean);
  }

  // PRINTING FUNCTIONS

  public static String toBinaryString(byte[] b) {
    StringBuilder s = new StringBuilder(b.length * 9);
    for (byte by : b) {
      for (int j = 7; j >= 0; j--) {
        s.append((by & (1 << j)) > 0 ? '1' : '0');
      }
      s.append(' ');
    }
    return s.toString();
  }

  public static String toString(double[] a) {
    return toString(a, null);
  }

  public static String toString(double[] a, NumberFormat nf) {
    if (a == null) return null;
    if (a.length == 0) return "[]";
    StringBuilder b = new StringBuilder(1000).append('[');
    for (int i = 0; i < a.length - 1; i++) {
      b.append(nf == null? String.valueOf(a[i]) : nf.format(a[i])).append(", ");
    }
    b.append(nf == null ? String.valueOf(a[a.length - 1]): nf.format(a[a.length - 1]));
    return b.append(']').toString();
  }

  public static String toString(float[] a) {
    return toString(a, null);
  }

  public static String toString(float[] a, NumberFormat nf) {
    if (a == null) return null;
    if (a.length == 0) return "[]";
    StringBuilder b = new StringBuilder(1000).append('[');
    for (int i = 0; i < a.length - 1; i++) {
      b.append(nf == null ? String.valueOf(a[i]) : nf.format(a[i])).append(", ");
    }
    b.append(nf == null ? String.valueOf(a[a.length - 1]): nf.format(a[a.length - 1]));
    return b.append(']').toString();
  }

  public static String toString(int[] a) {
    return toString(a, null);
  }

  public static String toString(int[] a, NumberFormat nf) {
    if (a == null) return null;
    if (a.length == 0) return "[]";
    StringBuilder b = new StringBuilder(1000).append('[');
    for (int i = 0; i < a.length - 1; i++) {
      b.append(nf == null ? String.valueOf(a[i]) : nf.format(a[i])).append(", ");
    }
    b.append(nf == null ? String.valueOf(a[a.length - 1]) : nf.format(a[a.length - 1]));
    return b.append(']').toString();
  }

  public static String toString(byte[] a) {
    return toString(a, null);
  }

  public static String toString(byte[] a, NumberFormat nf) {
    if (a == null) return null;
    if (a.length == 0) return "[]";
    StringBuilder b = new StringBuilder();
    b.append('[');
    for (int i = 0; i < a.length - 1; i++) {
      String s;
      if (nf == null) {
        s = String.valueOf(a[i]);
      } else {
        s = nf.format(a[i]);
      }
      b.append(s);
      b.append(", ");
    }
    String s;
    if (nf == null) {
      s = String.valueOf(a[a.length - 1]);
    } else {
      s = nf.format(a[a.length - 1]);
    }
    b.append(s);
    b.append(']');
    return b.toString();
  }

  public static String toString(int[][] counts) {
    return toString(counts, null, null, 10, 10, NumberFormat.getInstance(), false);
  }

  public static String toString(int[][] counts, Object[] rowLabels, Object[] colLabels, int labelSize, int cellSize, NumberFormat nf, boolean printTotals) {
    // first compute row totals and column totals
    if (counts.length==0 || counts[0].length==0) return "";
    int[] rowTotals = new int[counts.length];
    int[] colTotals = new int[counts[0].length]; // assume it's square
    int total = 0;
    for (int i = 0; i < counts.length; i++) {
      for (int j = 0; j < counts[i].length; j++) {
        rowTotals[i] += counts[i][j];
        colTotals[j] += counts[i][j];
        total += counts[i][j];
      }
    }
    StringBuilder result = new StringBuilder();
    // column labels
    if (colLabels != null) {
      result.append(StringUtils.padLeft("", labelSize)); // spacing for the row labels!
      for (int j = 0; j < counts[0].length; j++) {
        String s = (colLabels[j]==null ? "null" : colLabels[j].toString());
        if (s.length() > cellSize - 1) {
          s = s.substring(0, cellSize - 1);
        }
        s = StringUtils.padLeft(s, cellSize);
        result.append(s);
      }
      if (printTotals) {
        result.append(StringUtils.padLeftOrTrim("Total", cellSize));
      }
      result.append('\n');
    }
    for (int i = 0; i < counts.length; i++) {
      // row label
      if (rowLabels != null) {
        String s = (rowLabels[i]==null ? "null" : rowLabels[i].toString());
        s = StringUtils.padOrTrim(s, labelSize); // left align this guy only
        result.append(s);
      }
      // value
      for (int j = 0; j < counts[i].length; j++) {
        result.append(StringUtils.padLeft(nf.format(counts[i][j]), cellSize));
      }
      // the row total
      if (printTotals) {
        result.append(StringUtils.padLeft(nf.format(rowTotals[i]), cellSize));
      }
      result.append('\n');
    }
    // the col totals
    if (printTotals) {
      result.append(StringUtils.pad("Total", labelSize));
      for (int colTotal : colTotals) {
        result.append(StringUtils.padLeft(nf.format(colTotal), cellSize));
      }
      result.append(StringUtils.padLeft(nf.format(total), cellSize));
    }
    return result.toString();
  }


  public static String toString(double[][] counts) {
    return toString(counts, 10, null, null, NumberFormat.getInstance(), false);
  }

  public static String toString(double[][] counts, int cellSize, Object[] rowLabels, Object[] colLabels, NumberFormat nf, boolean printTotals) {
    return toString(counts, cellSize, rowLabels, colLabels, cellSize, nf, printTotals, printTotals, "");
  }

  public static String toString(double[][] counts, int cellSize, Object[] rowLabels, Object[] colLabels,
                                int rowLabelSize, NumberFormat nf, boolean printRowTotals, boolean printColumnTotals,
                                Object topLeft) {
    if (counts==null) return null;
    // first compute row totals and column totals
    double[] rowTotals = new double[counts.length];
    double[] colTotals = new double[counts[0].length]; // assume it's square
    double total = 0.0;
    for (int i = 0; i < counts.length; i++) {
      for (int j = 0; j < counts[i].length; j++) {
        rowTotals[i] += counts[i][j];
        colTotals[j] += counts[i][j];
        total += counts[i][j];
      }
    }
    StringBuilder result = new StringBuilder();
    // column labels
    if (colLabels != null) {
      result.append(StringUtils.padOrTrim(topLeft, rowLabelSize));
      for (int j = 0; j < counts[0].length; j++) {
        String s = colLabels[j].toString();
        if (s.length() > cellSize - 1) {
          s = s.substring(0, cellSize - 1);
        }
        s = StringUtils.padLeft(s, cellSize);
        result.append(s);
      }
      if (printRowTotals) {
        result.append(StringUtils.padLeftOrTrim("Total", cellSize));
      }
      result.append('\n');
    }
    for (int i = 0; i < counts.length; i++) {
      // row label
      if (rowLabels != null) {
        String s = rowLabels[i].toString();
        s = StringUtils.padOrTrim(s, rowLabelSize); // left align this guy only
        result.append(s);
      }
      // value
      for (int j = 0; j < counts[i].length; j++) {
        result.append(StringUtils.padLeft(nf.format(counts[i][j]), cellSize));
      }
      // the row total
      if (printRowTotals) {
        result.append(StringUtils.padLeft(nf.format(rowTotals[i]), cellSize));
      }
      result.append('\n');
    }
    // the col totals
    if (printColumnTotals) {
      result.append(StringUtils.pad("Total", rowLabelSize));
      for (double colTotal : colTotals) {
        result.append(StringUtils.padLeft(nf.format(colTotal), cellSize));
      }
      if (printRowTotals) {
        result.append(StringUtils.padLeft(nf.format(total), cellSize));
      }
    }
    return result.toString();
  }

  public static String toString(float[][] counts) {
    return toString(counts, 10, null, null, NumberFormat.getIntegerInstance(), false);
  }

  public static String toString(float[][] counts, int cellSize, Object[] rowLabels, Object[] colLabels, NumberFormat nf, boolean printTotals) {
    // first compute row totals and column totals
    double[] rowTotals = new double[counts.length];
    double[] colTotals = new double[counts[0].length]; // assume it's square
    double total = 0.0;
    for (int i = 0; i < counts.length; i++) {
      for (int j = 0; j < counts[i].length; j++) {
        rowTotals[i] += counts[i][j];
        colTotals[j] += counts[i][j];
        total += counts[i][j];
      }
    }
    StringBuilder result = new StringBuilder();
    // column labels
    if (colLabels != null) {
      result.append(StringUtils.padLeft("", cellSize));
      for (int j = 0; j < counts[0].length; j++) {
        String s = colLabels[j].toString();
        s = StringUtils.padLeftOrTrim(s, cellSize);
        result.append(s);
      }
      if (printTotals) {
        result.append(StringUtils.padLeftOrTrim("Total", cellSize));
      }
      result.append('\n');
    }
    for (int i = 0; i < counts.length; i++) {
      // row label
      if (rowLabels != null) {
        String s = rowLabels[i].toString();
        s = StringUtils.pad(s, cellSize); // left align this guy only
        result.append(s);
      }
      // value
      for (int j = 0; j < counts[i].length; j++) {
        result.append(StringUtils.padLeft(nf.format(counts[i][j]), cellSize));
      }
      // the row total
      if (printTotals) {
        result.append(StringUtils.padLeft(nf.format(rowTotals[i]), cellSize));
      }
      result.append('\n');
    }
    // the col totals
    if (printTotals) {
      result.append(StringUtils.pad("Total", cellSize));
      for (double colTotal : colTotals) {
        result.append(StringUtils.padLeft(nf.format(colTotal), cellSize));
      }
      result.append(StringUtils.padLeft(nf.format(total), cellSize));
    }
    return result.toString();
  }

  /**
   * For testing only.
   * @param args Ignored
   */
  public static void main(String[] args) {
    Random random = new Random();
    int length = 100;
    double[] A = new double[length];
    double[] B = new double[length];
    double aAvg = 70.0;
    double bAvg = 70.5;
    for (int i = 0; i < length; i++) {
      A[i] = aAvg + random.nextGaussian();
      B[i] = bAvg + random.nextGaussian();
    }
    System.out.println("A has length " + A.length + " and mean " + mean(A));
    System.out.println("B has length " + B.length + " and mean " + mean(B));
    for (int t = 0; t < 10; t++) {
      System.out.println("p-value: " + sigLevelByApproxRand(A, B));
    }
  }

  public static int[][] deepCopy(int[][] counts) {
    int[][] result = new int[counts.length][];
    for (int i=0; i<counts.length; i++) {
      result[i] = counts[i].clone();
    }
    return result;
  }

  public static void addMultInto(double[] a, double[] b, double[] c, double d) {
    for (int i=0; i<a.length; i++) {
      a[i] = b[i] + c[i] * d;
    }
  }

  public static void multiplyInto(double[] a, double[] b, double c) {
    for (int i=0; i<a.length; i++) {
      a[i] = b[i] * c;
    }
  }

  public static double entropy(double[] probs) {
    double e = 0.0;
    for (double p : probs) {
      if (p != 0.0)
        e -= p * Math.log(p);
    }
    return e;
  }

  public static void assertFinite(double[] vector, String vectorName) throws InvalidElementException {
    for(int i=0; i<vector.length; i++){
      if (Double.isNaN(vector[i]) || Double.isInfinite(vector[i])) {
        throw new InvalidElementException(vectorName + " element " + i + " is " + vector[i]);
      }
    }
  }

  public static class InvalidElementException extends RuntimeException {

    private static final long serialVersionUID = 1647150702529757545L;

    public InvalidElementException(String s) {
      super(s);
    }
  }

}
