package old.edu.stanford.nlp.util;

import java.lang.reflect.Array;
import java.util.*;


/**
 * Static utility methods for operating on arrays.
 *
 * @author Huy Nguyen (htnguyen@cs.stanford.edu)
 * @author Michel Galley (mgalley@stanford.edu)
 */
public class ArrayUtils {

  /**
   * Should not be instantiated
   */
  private ArrayUtils() {}

  public static double[] flatten(double[][] array) {
    int size = 0;
    for (double[] a : array) {
      size += a.length;
    }
    double[] newArray = new double[size];
    int i = 0;
    for (double[] a : array) {
      for (double d : a) {
        newArray[i++] = d;
      }
    }
    return newArray;
  }

  public static double[][] to2D(double[] array, int dim1Size) {
    int dim2Size = array.length/dim1Size;
    return to2D(array, dim1Size, dim2Size);
  }

  public static double[][] to2D(double[] array, int dim1Size, int dim2Size) {
    double[][] newArray = new double[dim1Size][dim2Size];
    int k = 0;
    for (int i = 0; i < newArray.length; i++) {
      for (int j = 0; j < newArray[i].length; j++) {
        newArray[i][j] = array[k++];
      }
    }
    return newArray;
  }

  /**
   * Removes the element at the specified index from the array, and returns
   * a new array containing the remaining elements.  If <tt>index</tt> is
   * invalid, returns <tt>array</tt> unchanged.
   */
  public static double[] removeAt(double[] array, int index) {
    if (array == null) {
      return null;
    }
    if (index < 0 || index >= array.length) {
      return array;
    }

    double[] retVal = new double[array.length - 1];
    for (int i = 0; i < array.length; i++) {
      if (i < index) {
        retVal[i] = array[i];
      } else if (i > index) {
        retVal[i - 1] = array[i];
      }
    }
    return retVal;
  }

  /**
   * Removes the element at the specified index from the array, and returns
   * a new array containing the remaining elements.  If <tt>index</tt> is
   * invalid, returns <tt>array</tt> unchanged.  Uses reflection to determine
   * the type of the array and returns an array of the appropriate type.
   */
  public static Object[] removeAt(Object[] array, int index) {
    if (array == null) {
      return null;
    }
    if (index < 0 || index >= array.length) {
      return array;
    }

    Object[] retVal = (Object[]) Array.newInstance(array[0].getClass(), array.length - 1);
    for (int i = 0; i < array.length; i++) {
      if (i < index) {
        retVal[i] = array[i];
      } else if (i > index) {
        retVal[i - 1] = array[i];
      }
    }
    return retVal;
  }

  public static String toString(int[][] a) {
    StringBuilder result = new StringBuilder("[");
    for (int i = 0; i < a.length; i++) {
      result.append(Arrays.toString(a[i]));
      if(i < a.length-1)
        result.append(',');
      }
    result.append(']');
    return result.toString();
  }

  /**
   * Tests two int[][] arrays for having equal contents.
   * @return true iff for each i, <code>equalContents(xs[i],ys[i])</code> is true
   */
  public static boolean equalContents(int[][] xs, int[][] ys) {
    if(xs ==null)
      return ys == null;
    if(ys == null)
      return false;
    if(xs.length != ys.length)
      return false;
    for(int i = xs.length-1; i >= 0; i--) {
      if(! equalContents(xs[i],ys[i]))
        return false;
    }
    return true;
  }

  /**
   * Tests two double[][] arrays for having equal contents.
   * @return true iff for each i, <code>equals(xs[i],ys[i])</code> is true
   */
  public static boolean equals(double[][] xs, double[][] ys) {
    if(xs == null)
      return ys == null;
    if(ys == null)
      return false;
    if(xs.length != ys.length)
      return false;
    for(int i = xs.length-1; i >= 0; i--) {
      if(!Arrays.equals(xs[i],ys[i]))
        return false;
    }
    return true;
  }


  /**
   * tests two int[] arrays for having equal contents
   * @return true iff xs and ys have equal length, and for each i, <code>xs[i]==ys[i]</code>
   */
  public static boolean equalContents(int[] xs, int[] ys) {
    if(xs.length != ys.length)
      return false;
    for(int i = xs.length-1; i >= 0; i--) {
      if(xs[i] != ys[i])
        return false;
    }
    return true;
  }

  /**
   * Tests two boolean[][] arrays for having equal contents.
   * @return true iff for each i, <code>Arrays.equals(xs[i],ys[i])</code> is true
   */
  @SuppressWarnings("null")
  public static boolean equals(boolean[][] xs, boolean[][] ys) {
    if(xs == null && ys != null)
      return false;
    if(ys == null)
      return false;
    if(xs.length != ys.length)
      return false;
    for(int i = xs.length-1; i >= 0; i--) {
      if(! Arrays.equals(xs[i],ys[i]))
        return false;
    }
    return true;
  }


  /** Returns true iff object o equals (not ==) some element of array a. */
  public static <T> boolean contains(T[] a, T o) {
    for (T item : a) {
      if (item.equals(o)) return true;
    }
    return false;
  }

  /** Return a set containing the same elements as the specified array.
   */
  public static <T> Set<T> asSet(T[] a) {
    return new HashSet<T>(Arrays.asList(a));
  }

  public static void fill(double[][] d, double val) {
    for (double[] aD : d) {
      Arrays.fill(aD, val);
    }
  }

  public static void fill(double[][][] d, double val) {
    for (double[][] aD : d) {
      fill(aD, val);
    }
  }

  public static void fill(double[][][][] d, double val) {
    for (double[][][] aD : d) {
      fill(aD, val);
    }
  }

  public static void fill(boolean[][] d, boolean val) {
    for (boolean[] aD : d) {
      Arrays.fill(aD, val);
    }
  }

  public static void fill(boolean[][][] d, boolean val) {
    for (boolean[][] aD : d) {
      fill(aD, val);
    }
  }

  public static void fill(boolean[][][][] d, boolean val) {
    for (boolean[][][] aD : d) {
      fill(aD, val);
    }
  }



  /**
  * Casts to a double array
  */
  public static double[] toDouble(float[] a) {
    double[] d = new double[a.length];
    for (int i = 0; i < a.length; i++) {
      d[i] = a[i];
    }
    return d;
  }

  /**
   * Casts to a double array.
   */
  public static double[] toDouble(int[] array) {
    double[] rv = new double[array.length];
    for (int i = 0; i < array.length; i++) {
      rv[i] = array[i];
    }
    return rv;
  }

  /** needed because Arrays.asList() won't to autoboxing,
   * so if you give it a primitive array you get a
   * singleton list back with just that array as an element.
   */
  public static List<Integer> asList(int[] array) {
    List<Integer> l = new ArrayList<Integer>();
    for (int i : array) {
      l.add(i);
    }
    return l;
  }


  public static double[] asPrimitiveDoubleArray(Collection<Double> d) {
    double[] newD = new double[d.size()];
    int i = 0;
    for (Double j : d) {
      newD[i++] = j;
    }
    return newD;
  }


  public static int[] asPrimitiveIntArray(Collection<Integer> d) {
    int[] newI = new int[d.size()];
    int i = 0;
    for (Integer j : d) {
      newI[i++] = j;
    }
    return newI;
  }


  public static int[] copy(int[] i) {
    if (i == null) { return null; }
    int[] newI = new int[i.length];
    System.arraycopy(i, 0, newI, 0, i.length);
    return newI;
  }

  public static int[][] copy(int[][] i) {
    if (i == null) { return null; }
    int[][] newI = new int[i.length][];
    for (int j = 0; j < newI.length; j++) {
      newI[j] = copy(i[j]);
    }
    return newI;
  }


  public static double[] copy(double[] d) {
    if (d == null) { return null; }
    double[] newD = new double[d.length];
    System.arraycopy(d, 0, newD, 0, d.length);
    return newD;
  }

  public static double[][] copy(double[][] d) {
    if (d == null) { return null; }
    double[][] newD = new double[d.length][];
    for (int i = 0; i < newD.length; i++) {
      newD[i] = copy(d[i]);
    }
    return newD;
  }

  public static double[][][] copy(double[][][] d) {
    if (d == null) { return null; }
    double[][][] newD = new double[d.length][][];
    for (int i = 0; i < newD.length; i++) {
      newD[i] = copy(d[i]);
    }
    return newD;
  }

  public static float[] copy(float[] d) {
    if (d == null) { return null; }
    float[] newD = new float[d.length];
    System.arraycopy(d, 0, newD, 0, d.length);
    return newD;
  }

  public static float[][] copy(float[][] d) {
    if (d == null) { return null; }
    float[][] newD = new float[d.length][];
    for (int i = 0; i < newD.length; i++) {
      newD[i] = copy(d[i]);
    }
    return newD;
  }

  public static float[][][] copy(float[][][] d) {
    if (d == null) { return null; }
    float[][][] newD = new float[d.length][][];
    for (int i = 0; i < newD.length; i++) {
      newD[i] = copy(d[i]);
    }
    return newD;
  }


  public static String toString(boolean[][] b) {
    StringBuilder result = new StringBuilder("[");
    for (int i = 0; i < b.length; i++) {
      result.append(Arrays.toString(b[i]));
      if(i < b.length-1)
        result.append(',');
      }
    result.append(']');
    return result.toString();
  }

  public static long[] toPrimitive(Long[] in) {
    return toPrimitive(in,0L);
  }

  public static int[] toPrimitive(Integer[] in) {
    return toPrimitive(in,0);
  }

  public static short[] toPrimitive(Short[] in) {
    return toPrimitive(in,(short)0);
  }

  public static char[] toPrimitive(Character[] in) {
    return toPrimitive(in,(char)0);
  }

  public static double[] toPrimitive(Double[] in) {
    return toPrimitive(in,0.0);
  }

  public static long[] toPrimitive(Long[] in, long valueForNull) {
    if (in == null)
      return null;
    final long[] out = new long[in.length];
    for (int i = 0; i < in.length; i++) {
      Long b = in[i];
      out[i] = (b == null ? valueForNull : b);
    }
    return out;
  }

  public static int[] toPrimitive(Integer[] in, int valueForNull) {
    if (in == null)
      return null;
    final int[] out = new int[in.length];
    for (int i = 0; i < in.length; i++) {
      Integer b = in[i];
      out[i] = (b == null ? valueForNull : b);
    }
    return out;
  }

   public static short[] toPrimitive(Short[] in, short valueForNull) {
    if (in == null)
      return null;
    final short[] out = new short[in.length];
    for (int i = 0; i < in.length; i++) {
      Short b = in[i];
      out[i] = (b == null ? valueForNull : b);
    }
    return out;
  }

   public static char[] toPrimitive(Character[] in, char valueForNull) {
    if (in == null)
      return null;
    final char[] out = new char[in.length];
    for (int i = 0; i < in.length; i++) {
      Character b = in[i];
      out[i] = (b == null ? valueForNull : b);
    }
    return out;
  }

  public static double[] toPrimitive(Double[] in, double valueForNull) {
    if (in == null)
      return null;
    final double[] out = new double[in.length];
    for (int i = 0; i < in.length; i++) {
      Double b = in[i];
      out[i] = (b == null ? valueForNull : b);
    }
    return out;
  }
}
