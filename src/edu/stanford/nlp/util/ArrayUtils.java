package edu.stanford.nlp.util;

import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Predicate;

import edu.stanford.nlp.util.logging.Redwood;


/**
 * Static utility methods for operating on arrays.
 *
 * Note: You can also find some methods for printing arrays that are tables in
 * StringUtils.  (Search for makeTextTable, etc.)
 *
 * @author Huy Nguyen (htnguyen@cs.stanford.edu)
 * @author Michel Galley (mgalley@stanford.edu)
 */
public class ArrayUtils  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(ArrayUtils.class);

  /**
   * Should not be instantiated
   */
  private ArrayUtils() {}

  /**
   * Gap-encodes a sorted array of ints into bytes, using the scheme
   * described in {@link #gapEncodeList(int[])}.
   *
   * @param orig The values to encode, sorted in nondecreasing order
   * @return The encoded bytes
   * @throws IllegalArgumentException if {@code orig} is not sorted
   */
  public static byte[] gapEncode(int[] orig) {
    List<Byte> encodedList = gapEncodeList(orig);
    byte[] arr = new byte[encodedList.size()];
    int i = 0;
    for (byte b : encodedList) { arr[i++] = b; }
    return arr;
  }

  /**
   * Gap-encodes a sorted array of ints as a list of bytes.  Each value is stored
   * as its difference from the previous value (the first from 0): the number of
   * binary digits of the gap in unary (1s followed by a 0), then the binary digits
   * of the gap after the leading 1.  A repeated value is stored as a single 0 bit.
   * The last byte is padded with 1 bits.
   *
   * @param orig The values to encode, sorted in nondecreasing order
   * @return The encoded bytes
   * @throws IllegalArgumentException if {@code orig} is not sorted
   */
  public static List<Byte> gapEncodeList(int[] orig) {
    for (int i = 1; i < orig.length; i++) {
      if (orig[i] < orig[i-1]) {
        throw new IllegalArgumentException("Array must be sorted!");
      }
    }

    List<Byte> bytes = new ArrayList<>();

    int index = 0;
    int prevNum = 0;
    byte currByte = 0 << 8;

    for (int f : orig) {
      String n = (f == prevNum ? "" : Integer.toString(f-prevNum, 2));
      for (int ii = 0; ii < n.length(); ii++) {
        if (index == 8) {
          bytes.add(currByte);
          currByte = 0 << 8;
          index = 0;
        }
        currByte <<= 1;
        currByte++;
        index++;
      }

      if (index == 8) {
        bytes.add(currByte);
        currByte = 0 << 8;
        index = 0;
      }
      currByte <<= 1;
      index++;

      for (int i = 1; i < n.length(); i++) {
        if (index == 8) {
          bytes.add(currByte);
          currByte = 0 << 8;
          index = 0;
        }
        currByte <<= 1;
        if (n.charAt(i) == '1') {
          currByte++;
        }
        index++;
      }
      prevNum = f;
    }

    while (index > 0 && index < 9) {
      if (index == 8) {
        bytes.add(currByte);
        break;
      }
      currByte <<= 1;
      currByte++;
      index++;
    }

    return bytes;
  }

  /**
   * Decodes an array produced by {@link #gapEncode(int[])}.
   *
   * @param gapEncoded The encoded bytes
   * @return The decoded values
   */
  public static int[] gapDecode(byte[] gapEncoded) {
    return gapDecode(gapEncoded, 0, gapEncoded.length);
  }

  /**
   * Decodes the bytes in the range [startIndex, endIndex) of an array
   * produced by {@link #gapEncode(int[])}.
   *
   * @param gapEncoded The encoded bytes
   * @param startIndex Index of the first byte to decode
   * @param endIndex Index one past the last byte to decode
   * @return The decoded values
   */
  public static int[] gapDecode(byte[] gapEncoded, int startIndex, int endIndex) {
    List<Integer> ints = gapDecodeList(gapEncoded, startIndex, endIndex);
    int[] arr = new int[ints.size()];
    int index = 0;
    for (int i : ints) { arr[index++] = i; }
    return arr;
  }

  /**
   * Decodes an array produced by {@link #gapEncode(int[])} into a list.
   *
   * @param gapEncoded The encoded bytes
   * @return The decoded values
   */
  public static List<Integer> gapDecodeList(byte[] gapEncoded) {
    return gapDecodeList(gapEncoded, 0, gapEncoded.length);
  }

  /**
   * Decodes the bytes in the range [startIndex, endIndex) of an array
   * produced by {@link #gapEncode(int[])} into a list.
   *
   * @param gapEncoded The encoded bytes
   * @param startIndex Index of the first byte to decode
   * @param endIndex Index one past the last byte to decode
   * @return The decoded values
   */
  public static List<Integer> gapDecodeList(byte[] gapEncoded, int startIndex, int endIndex) {

    boolean gettingSize = true;
    int size = 0;
    List<Integer> ints = new ArrayList<>();
    int gap = 0;
    int prevNum = 0;

    for (int i = startIndex; i < endIndex; i++) {
      byte b = gapEncoded[i];
      for (int index = 7; index >= 0; index--) {
        boolean value = ((b >> index) & 1) == 1;

        if (gettingSize) {
          if (value) { size++; }
          else {
            if (size == 0) {
              ints.add(prevNum);
            } else if (size == 1) {
              prevNum++;
              ints.add(prevNum);
              size = 0;
            } else {
              gettingSize = false;
              gap = 1;
              size--;
            }
          }
        } else {
          gap <<= 1;
          if (value) { gap++; }
          size--;
          if (size == 0) {
            prevNum += gap;
            ints.add(prevNum);
            gettingSize = true;
          }
        }
      }
    }

    return ints;
  }

  /**
   * Delta-encodes a sorted array of ints into bytes, using the scheme
   * described in {@link #deltaEncodeList(int[])}.
   *
   * @param orig The values to encode, sorted in nondecreasing order
   * @return The encoded bytes
   * @throws IllegalArgumentException if {@code orig} is not sorted
   */
  public static byte[] deltaEncode(int[] orig) {
    List<Byte> encodedList = deltaEncodeList(orig);
    byte[] arr = new byte[encodedList.size()];
    int i = 0;
    for (byte b : encodedList) { arr[i++] = b; }
    return arr;
  }

  /**
   * Delta-encodes a sorted array of ints as a list of bytes.  Like
   * {@link #gapEncodeList(int[])}, each value is stored as its gap from the
   * previous value, but the length of the gap is itself written in the gap
   * encoding (unary length of the length, then its remaining binary digits),
   * followed by the binary digits of the gap after the leading 1.  A repeated
   * value is stored as a single 0 bit.  The last byte is padded with 1 bits.
   *
   * @param orig The values to encode, sorted in nondecreasing order
   * @return The encoded bytes
   * @throws IllegalArgumentException if {@code orig} is not sorted
   */
  public static List<Byte> deltaEncodeList(int[] orig) {

    for (int i = 1; i < orig.length; i++) {
      if (orig[i] < orig[i-1]) {
        throw new IllegalArgumentException("Array must be sorted!");
      }
    }

    List<Byte> bytes = new ArrayList<>();

    int index = 0;
    int prevNum = 0;
    byte currByte = 0 << 8;

    for (int f : orig) {
      String n = (f == prevNum ? "" : Integer.toString(f-prevNum, 2));
      String n1 = (n.isEmpty() ? "" : Integer.toString(n.length(), 2));
      for (int ii = 0; ii < n1.length(); ii++) {
        if (index == 8) {
          bytes.add(currByte);
          currByte = 0 << 8;
          index = 0;
        }
        currByte <<= 1;
        currByte++;
        index++;
      }

      if (index == 8) {
        bytes.add(currByte);
        currByte = 0 << 8;
        index = 0;
      }
      currByte <<= 1;
      index++;

      for (int i = 1; i < n1.length(); i++) {
        if (index == 8) {
          bytes.add(currByte);
          currByte = 0 << 8;
          index = 0;
        }
        currByte <<= 1;
        if (n1.charAt(i) == '1') {
          currByte++;
        }
        index++;
      }

      for (int i = 1; i < n.length(); i++) {
        if (index == 8) {
          bytes.add(currByte);
          currByte = 0 << 8;
          index = 0;
        }
        currByte <<= 1;
        if (n.charAt(i) == '1') {
          currByte++;
        }
        index++;
      }

      prevNum = f;

    }

    while (index > 0 && index < 9) {
      if (index == 8) {
        bytes.add(currByte);
        break;
      }
      currByte <<= 1;
      currByte++;
      index++;
    }

    return bytes;
  }


  /**
   * Decodes an array produced by {@link #deltaEncode(int[])}.
   *
   * @param deltaEncoded The encoded bytes
   * @return The decoded values
   */
  public static int[] deltaDecode(byte[] deltaEncoded) {
    return deltaDecode(deltaEncoded, 0, deltaEncoded.length);
  }

  /**
   * Decodes an array produced by {@link #deltaEncode(int[])}.
   * Note that {@code startIndex} and {@code endIndex} are ignored:
   * the whole array is always decoded.
   *
   * @param deltaEncoded The encoded bytes
   * @param startIndex Ignored
   * @param endIndex Ignored
   * @return The decoded values
   */
  public static int[] deltaDecode(byte[] deltaEncoded, int startIndex, int endIndex) {
    List<Integer> ints = deltaDecodeList(deltaEncoded);
    int[] arr = new int[ints.size()];
    int index = 0;
    for (int i : ints) { arr[index++] = i; }
    return arr;
  }

  /**
   * Decodes an array produced by {@link #deltaEncode(int[])} into a list.
   *
   * @param deltaEncoded The encoded bytes
   * @return The decoded values
   */
  public static List<Integer> deltaDecodeList(byte[] deltaEncoded) {
    return deltaDecodeList(deltaEncoded, 0, deltaEncoded.length);
  }

  /**
   * Decodes the bytes in the range [startIndex, endIndex) of an array
   * produced by {@link #deltaEncode(int[])} into a list.
   *
   * @param deltaEncoded The encoded bytes
   * @param startIndex Index of the first byte to decode
   * @param endIndex Index one past the last byte to decode
   * @return The decoded values
   */
  public static List<Integer> deltaDecodeList(byte[] deltaEncoded, int startIndex, int endIndex) {

    boolean gettingSize1 = true;
    boolean gettingSize2 = false;
    int size1 = 0;
    List<Integer> ints = new ArrayList<>();
    int gap = 0;
    int size2 = 0;
    int prevNum = 0;

    for (int i = startIndex; i < endIndex; i++) {
      byte b = deltaEncoded[i];
      for (int index = 7; index >= 0; index--) {
        boolean value = ((b >> index) & 1) == 1;

        if (gettingSize1) {
          if (value) { size1++; }
          else {
            if (size1 == 0) {
              ints.add(prevNum);
            } else if (size1 == 1) {
              prevNum++;
              ints.add(prevNum);
              size1 = 0;
            } else {
              gettingSize1 = false;
              gettingSize2 = true;
              size2 = 1;
              size1--;
            }
          }
        } else if (gettingSize2) {
          size2 <<= 1;
          if (value) { size2++; }
          size1--;
          if (size1 == 0) {
            gettingSize2 = false;
            gap = 1;
            size2--;
          }
        } else {
          gap <<= 1;
          if (value) { gap++; }
          size2--;
          if (size2 == 0) {
            prevNum += gap;
            ints.add(prevNum);
            gettingSize1 = true;
          }
        }
      }
    }

    return ints;
  }

  /** helper for gap encoding. */
  private static byte[] bitSetToByteArray(BitSet bitSet) {

    while (bitSet.length() % 8 != 0) { bitSet.set(bitSet.length(), true); }

    byte[] array = new byte[bitSet.length()/8];

    for (int i = 0; i < array.length; i++) {
      int offset = i * 8;

      int index = 0;
      for (int j = 0; j < 8; j++) {
        index <<= 1;
        if (bitSet.get(offset+j)) { index++; }
      }

      array[i] = (byte)(index - 128);
    }

    return array;
  }

  /** helper for gap encoding. */
  private static BitSet byteArrayToBitSet(byte[] array) {

    BitSet bitSet = new BitSet();
    int index = 0;
    for (byte b : array) {
      int b1 = ((int)b) + 128;

      bitSet.set(index++, ((b1 >> 7) & 1) == 1);
      bitSet.set(index++, ((b1 >> 6) & 1) == 1);
      bitSet.set(index++, ((b1 >> 5) & 1) == 1);
      bitSet.set(index++, ((b1 >> 4) & 1) == 1);
      bitSet.set(index++, ((b1 >> 3) & 1) == 1);
      bitSet.set(index++, ((b1 >> 2) & 1) == 1);
      bitSet.set(index++, ((b1 >> 1) & 1) == 1);
      bitSet.set(index++, (b1 & 1) == 1);
    }

    return bitSet;
  }

//     for (int i = 1; i < orig.length; i++) {
//       if (orig[i] < orig[i-1]) { throw new RuntimeException("Array must be sorted!"); }

//       StringBuilder bits = new StringBuilder();
//       int prevNum = 0;
//       for (int f : orig) {
//         StringBuilder bits1 = new StringBuilder();
//               log.info(f+"\t");
//               String n = Integer.toString(f-prevNum, 2);
//               String n1 = Integer.toString(n.length(), 2);
//               for (int ii = 0; ii < n1.length(); ii++) {
//                 bits1.append("1");
//               }
//               bits1.append("0");
//               bits1.append(n1.substring(1));
//               bits1.append(n.substring(1));
//               log.info(bits1+"\t");
//               bits.append(bits1);
//               prevNum = f;
//             }



  /**
   * Concatenates the rows of a 2D array into a new 1D array.
   *
   * @param array The 2D array (rows may have different lengths)
   * @return A new array holding all the elements in row-major order
   */
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

  /**
   * Reshapes a 1D array into a new 2D array with {@code dim1Size} rows,
   * each of length {@code array.length / dim1Size}.  Any leftover elements are dropped.
   *
   * @param array The values, in row-major order
   * @param dim1Size The number of rows
   * @return A new 2D array
   */
  public static double[][] to2D(double[] array, int dim1Size) {
    int dim2Size = array.length/dim1Size;
    return to2D(array, dim1Size, dim2Size);
  }

  /**
   * Reshapes a 1D array into a new {@code dim1Size} by {@code dim2Size} 2D array,
   * filled in row-major order.
   *
   * @param array The values, in row-major order; must have at least
   *     {@code dim1Size * dim2Size} elements
   * @param dim1Size The number of rows
   * @param dim2Size The number of columns
   * @return A new 2D array
   */
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
   * a new array containing the remaining elements.  If {@code index} is
   * invalid, returns {@code array} unchanged.
   *
   * @param array The array (may be null)
   * @param index The index of the element to remove
   * @return A new array without that element, {@code array} itself if
   *     {@code index} is out of range, or null if {@code array} is null
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
   * a new array containing the remaining elements.  If {@code index} is
   * invalid, returns {@code array} unchanged.  Uses reflection to determine
   * the type of the array and returns an array of the appropriate type.
   * The type used is the runtime class of {@code array[0]}, not the
   * component type of {@code array}.
   *
   * @param array The array (may be null)
   * @param index The index of the element to remove
   * @return A new array without that element, {@code array} itself if
   *     {@code index} is out of range, or null if {@code array} is null
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

  /**
   * Returns a string representation of a 2D int array, with each row
   * formatted by {@link Arrays#toString(int[])}.
   *
   * @param a The array
   * @return A string such as {@code [[1, 2],[3]]}
   */
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
   * Two null arrays are considered equal.
   *
   * @param xs The first array
   * @param ys The second array
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
   * Two null arrays are considered equal.
   *
   * @param xs The first array
   * @param ys The second array
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
   *
   * @param xs The first array (must not be null)
   * @param ys The second array (must not be null)
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
   * Returns false if either array is null, including when both are.
   *
   * @param xs The first array
   * @param ys The second array
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


  /**
   * Returns true iff object o equals (not ==) some element of array a.
   * Calls {@code equals} on the array elements, so null elements cause a
   * {@link NullPointerException}.
   *
   * @param <T> The element type
   * @param a The array to search
   * @param o The object to look for
   * @return Whether some element of {@code a} equals {@code o}
   */
  public static <T> boolean contains(T[] a, T o) {
    for (T item : a) {
      if (item.equals(o)) return true;
    }
    return false;
  }

  // from stackoverflow
  //  http://stackoverflow.com/questions/80476/how-to-concatenate-two-arrays-in-java
  /**
   * Concatenates two arrays and returns the result
   *
   * @param <T> The element type
   * @param first The first array
   * @param second The second array
   * @return A new array, of the same runtime type as {@code first}, holding the
   *     elements of {@code first} followed by those of {@code second}
   */
  public static <T> T[] concatenate(T[] first, T[] second) {
    T[] result = Arrays.copyOf(first, first.length + second.length);
    System.arraycopy(second, 0, result, first.length, second.length);
    return result;
  }

  /**
   * Returns an array with only the elements accepted by <code>filter</code>
   * <br>
   * Implementation notes: creates two arrays, calls <code>filter</code>
   * once for each element, does not alter <code>original</code>
   *
   * @param <T> The element type
   * @param original The array to filter
   * @param filter The test an element must pass to be kept
   * @return A new array holding the accepted elements, in their original order
   */
  public static <T> T[] filter(T[] original, Predicate<? super T> filter) {
    T[] result = Arrays.copyOf(original, original.length); // avoids generic array creation compile error
    int size = 0;
    for (T value : original) {
      if (filter.test(value)) {
        result[size] = value;
        size++;
      }
    }
    if (size == original.length) {
      return result;
    }
    return Arrays.copyOf(result, size);
  }

  /** Return a Set containing the same elements as the specified array.
   *
   * @param <T> The element type
   * @param a The array
   * @return A new (mutable) HashSet of the elements
   */
  public static <T> Set<T> asSet(T[] a) {
    return Generics.newHashSet(Arrays.asList(a));
  }

  /** Return an immutable Set containing the same elements as the specified
   *  array. Arrays with 0 or 1 elements are special cased to return the
   *  efficient small sets from the Collections class.
   *
   * @param <T> The element type
   * @param a The array
   * @return An unmodifiable Set of the elements
   */
  public static <T> Set<T> asImmutableSet(T[] a) {
    if (a.length == 0) {
      return Collections.emptySet();
    } else if (a.length == 1) {
      return Collections.singleton(a[0]);
    } else {
      return Collections.unmodifiableSet(Generics.newHashSet(Arrays.asList(a)));
    }
  }

  /**
   * Sets every element of a 2D array to {@code val}.
   *
   * @param d The array to fill (modified in place)
   * @param val The value to store
   */
  public static void fill(double[][] d, double val) {
    for (double[] aD : d) {
      Arrays.fill(aD, val);
    }
  }

  /**
   * Sets every element of a 3D array to {@code val}.
   *
   * @param d The array to fill (modified in place)
   * @param val The value to store
   */
  public static void fill(double[][][] d, double val) {
    for (double[][] aD : d) {
      fill(aD, val);
    }
  }

  /**
   * Sets every element of a 4D array to {@code val}.
   *
   * @param d The array to fill (modified in place)
   * @param val The value to store
   */
  public static void fill(double[][][][] d, double val) {
    for (double[][][] aD : d) {
      fill(aD, val);
    }
  }

  /**
   * Sets every element of a 2D array to {@code val}.
   *
   * @param d The array to fill (modified in place)
   * @param val The value to store
   */
  public static void fill(boolean[][] d, boolean val) {
    for (boolean[] aD : d) {
      Arrays.fill(aD, val);
    }
  }

  /**
   * Sets every element of a 3D array to {@code val}.
   *
   * @param d The array to fill (modified in place)
   * @param val The value to store
   */
  public static void fill(boolean[][][] d, boolean val) {
    for (boolean[][] aD : d) {
      fill(aD, val);
    }
  }

  /**
   * Sets every element of a 4D array to {@code val}.
   *
   * @param d The array to fill (modified in place)
   * @param val The value to store
   */
  public static void fill(boolean[][][][] d, boolean val) {
    for (boolean[][][] aD : d) {
      fill(aD, val);
    }
  }



  /**
  * Casts to a double array
  *
  * @param a The array to convert
  * @return A new double array with the same values
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
   *
   * @param array The array to convert
   * @return A new double array with the same values
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
   *
   * @param array The values
   * @return A new (mutable) list of the boxed values
   */
  public static List<Integer> asList(int[] array) {
    List<Integer> l = new ArrayList<>();
    for (int i : array) {
      l.add(i);
    }
    return l;
  }


  /**
   * Copies a collection of Doubles into a new primitive array, in iteration order.
   *
   * @param d The values; must not contain null
   * @return A new double array
   */
  public static double[] asPrimitiveDoubleArray(Collection<Double> d) {
    double[] newD = new double[d.size()];
    int i = 0;
    for (Double j : d) {
      newD[i++] = j;
    }
    return newD;
  }


  /**
   * Copies a collection of Integers into a new primitive array, in iteration order.
   *
   * @param d The values; must not contain null
   * @return A new int array
   */
  public static int[] asPrimitiveIntArray(Collection<Integer> d) {
    int[] newI = new int[d.size()];
    int i = 0;
    for (Integer j : d) {
      newI[i++] = j;
    }
    return newI;
  }

  /**
   * Returns a copy of the array.
   *
   * @param arr The array to copy
   * @return A new array with the same contents, or null if {@code arr} is null
   */
  public static long[] copy(long[] arr) {
    if (arr == null) { return null; }
    long[] newArr = new long[arr.length];
    System.arraycopy(arr, 0, newArr, 0, arr.length);
    return newArr;
  }

  /**
   * Returns a copy of the array.
   *
   * @param i The array to copy
   * @return A new array with the same contents, or null if {@code i} is null
   */
  public static int[] copy(int[] i) {
    if (i == null) { return null; }
    int[] newI = new int[i.length];
    System.arraycopy(i, 0, newI, 0, i.length);
    return newI;
  }

  /**
   * Returns a deep copy of the 2D array.
   *
   * @param i The array to copy
   * @return A new array with copies of each row, or null if {@code i} is null
   */
  public static int[][] copy(int[][] i) {
    if (i == null) { return null; }
    int[][] newI = new int[i.length][];
    for (int j = 0; j < newI.length; j++) {
      newI[j] = copy(i[j]);
    }
    return newI;
  }


  /**
   * Returns a copy of the array.
   *
   * @param arr The array to copy
   * @return A new array with the same contents, or null if {@code arr} is null
   */
  public static short[] copy(short[] arr) {
    if (arr == null) { return null; }
    short[] newArr = new short[arr.length];
    System.arraycopy(arr, 0, newArr, 0, arr.length);
    return newArr;
  }

  /**
   * Returns a copy of the array.
   *
   * @param d The array to copy
   * @return A new array with the same contents, or null if {@code d} is null
   */
  public static double[] copy(double[] d) {
    if (d == null) { return null; }
    double[] newD = new double[d.length];
    System.arraycopy(d, 0, newD, 0, d.length);
    return newD;
  }

  /**
   * Returns a deep copy of the 2D array.
   *
   * @param d The array to copy
   * @return A new array with copies of each row, or null if {@code d} is null
   */
  public static double[][] copy(double[][] d) {
    if (d == null) { return null; }
    double[][] newD = new double[d.length][];
    for (int i = 0; i < newD.length; i++) {
      newD[i] = copy(d[i]);
    }
    return newD;
  }

  /**
   * Returns a deep copy of the 3D array.
   *
   * @param d The array to copy
   * @return A new array with deep copies of each element, or null if {@code d} is null
   */
  public static double[][][] copy(double[][][] d) {
    if (d == null) { return null; }
    double[][][] newD = new double[d.length][][];
    for (int i = 0; i < newD.length; i++) {
      newD[i] = copy(d[i]);
    }
    return newD;
  }

  /**
   * Returns a copy of the array.
   *
   * @param d The array to copy
   * @return A new array with the same contents, or null if {@code d} is null
   */
  public static float[] copy(float[] d) {
    if (d == null) { return null; }
    float[] newD = new float[d.length];
    System.arraycopy(d, 0, newD, 0, d.length);
    return newD;
  }

  /**
   * Returns a deep copy of the 2D array.
   *
   * @param d The array to copy
   * @return A new array with copies of each row, or null if {@code d} is null
   */
  public static float[][] copy(float[][] d) {
    if (d == null) { return null; }
    float[][] newD = new float[d.length][];
    for (int i = 0; i < newD.length; i++) {
      newD[i] = copy(d[i]);
    }
    return newD;
  }

  /**
   * Returns a deep copy of the 3D array.
   *
   * @param d The array to copy
   * @return A new array with deep copies of each element, or null if {@code d} is null
   */
  public static float[][][] copy(float[][][] d) {
    if (d == null) { return null; }
    float[][][] newD = new float[d.length][][];
    for (int i = 0; i < newD.length; i++) {
      newD[i] = copy(d[i]);
    }
    return newD;
  }

  /**
   * Returns a string representation of a 2D double array, with each row
   * formatted by {@link Arrays#toString(double[])}.
   *
   * @param b The array
   * @return A string such as {@code [[1.0, 2.0],[3.0]]}
   */
  public static String toString(double[][] b) {
    StringBuilder result = new StringBuilder("[");
    for (int i = 0; i < b.length; i++) {
      result.append(Arrays.toString(b[i]));
      if(i < b.length-1)
        result.append(',');
      }
    result.append(']');
    return result.toString();
  }

  /**
   * Returns a string representation of a 2D boolean array, with each row
   * formatted by {@link Arrays#toString(boolean[])}.
   *
   * @param b The array
   * @return A string such as {@code [[true, false],[true]]}
   */
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

  /**
   * Converts an array of Longs to primitives, mapping null elements to 0.
   *
   * @param in The array to convert
   * @return A new primitive array, or null if {@code in} is null
   */
  public static long[] toPrimitive(Long[] in) {
    return toPrimitive(in,0L);
  }

  /**
   * Converts an array of Integers to primitives, mapping null elements to 0.
   *
   * @param in The array to convert
   * @return A new primitive array, or null if {@code in} is null
   */
  public static int[] toPrimitive(Integer[] in) {
    return toPrimitive(in,0);
  }

  /**
   * Converts an array of Shorts to primitives, mapping null elements to 0.
   *
   * @param in The array to convert
   * @return A new primitive array, or null if {@code in} is null
   */
  public static short[] toPrimitive(Short[] in) {
    return toPrimitive(in,(short)0);
  }

  /**
   * Converts an array of Characters to primitives, mapping null elements to {@code (char) 0}.
   *
   * @param in The array to convert
   * @return A new primitive array, or null if {@code in} is null
   */
  public static char[] toPrimitive(Character[] in) {
    return toPrimitive(in,(char)0);
  }

  /**
   * Converts an array of Doubles to primitives, mapping null elements to 0.0.
   *
   * @param in The array to convert
   * @return A new primitive array, or null if {@code in} is null
   */
  public static double[] toPrimitive(Double[] in) {
    return toPrimitive(in,0.0);
  }

  /**
   * Converts an array of Longs to primitives.
   *
   * @param in The array to convert
   * @param valueForNull The value to use for null elements
   * @return A new primitive array, or null if {@code in} is null
   */
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

  /**
   * Converts an array of Integers to primitives.
   *
   * @param in The array to convert
   * @param valueForNull The value to use for null elements
   * @return A new primitive array, or null if {@code in} is null
   */
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

   /**
    * Converts an array of Shorts to primitives.
    *
    * @param in The array to convert
    * @param valueForNull The value to use for null elements
    * @return A new primitive array, or null if {@code in} is null
    */
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

   /**
    * Converts an array of Characters to primitives.
    *
    * @param in The array to convert
    * @param valueForNull The value to use for null elements
    * @return A new primitive array, or null if {@code in} is null
    */
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

  /**
   * Parses each string with {@link Double#parseDouble(String)}.
   *
   * @param in The strings to parse
   * @return A new array of the parsed values
   * @throws NumberFormatException if a string is not a parsable double
   */
  public static double[] toDoubleArray(String[] in) {
    double[] ret = new double[in.length];
    for (int i = 0; i < in.length; i++)
      ret[i] = Double.parseDouble(in[i]);

    return ret;
  }

  /**
   * Converts an array of Doubles to primitives.
   *
   * @param in The array to convert
   * @param valueForNull The value to use for null elements
   * @return A new primitive array, or null if {@code in} is null
   */
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

  /**
   * Provides a consistent ordering over arrays. First compares by the
   * first element. If that element is equal, the next element is
   * considered, and so on. This is the array version of
   * {@link edu.stanford.nlp.util.CollectionUtils#compareLists}
   * and uses the same logic when the arrays are of different lengths.
   *
   * @param <T> The element type
   * @param first The first array
   * @param second The second array
   * @return A negative number, zero, or a positive number as {@code first} is
   *     less than, equal to, or greater than {@code second}
   */
  public static <T extends Comparable<T>> int compareArrays(T[] first, T[] second) {
    List<T> firstAsList = Arrays.asList(first);
    List<T> secondAsList = Arrays.asList(second);
    return CollectionUtils.compareLists(firstAsList, secondAsList);
  }

  /* -- This is an older more direct implementation of the above, but not necessary unless for performance
   public static <C extends Comparable<C>> int compareArrays(C[] a1, C[] a2) {
    int len = Math.min(a1.length, a2.length);
    for (int i = 0; i < len; i++) {
      int comparison = a1[i].compareTo(a2[i]);
      if (comparison != 0) return comparison;
    }
    // one is a prefix of the other, or they're identical
    if (a1.length < a2.length) return -1;
    if (a1.length > a2.length) return 1;
    return 0;
  }
   */

  /**
   * Returns the index of the first occurrence of the specified element in this array starting at the specified index,
   *   or -1 if this array does not contain the element.
   * @param <T> The element type
   * @param array Array to search
   * @param object Object to look for
   * @param startIndex Start index
   * @return index of the specified element in the array, or -1 if the array does not contain the element
   */
  public static <T> int indexOf(T[] array, T object, int startIndex) {
    for (int i = startIndex; i < array.length; i++) {
      if (object.equals(array[i])) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Returns the index of the first occurrence of the specified element in this array,
   *   or -1 if this array does not contain the element.
   * @param <T> The element type
   * @param array Array to search
   * @param object Object to look for
   * @return index of the specified element in the array, or -1 if the array does not contain the element
   */
  public static <T> int indexOf(T[] array, T object) {
    return indexOf(array, object, 0);
  }

  /**
   * Finds the starting indices of occurrences of {@code tofind} in {@code tokens},
   * comparing elements with {@code equals}.  See
   * {@link #getSubListIndex(Object[], Object[], Predicate)}.
   *
   * @param tofind The array to search for
   * @param tokens The array to search in
   * @return The starting indices of the matches, or null if {@code tofind} is
   *     longer than {@code tokens}
   */
  public static List<Integer> getSubListIndex(Object[] tofind, Object[] tokens){
     return getSubListIndex(tofind, tokens, (o1) -> o1.first().equals(o1.second()));
  }

  /**
   * If tofind is a part of tokens, it finds the ****starting index***** of tofind in tokens
   * of each occurrence.
   * If tofind is longer than tokens, it returns null; otherwise, if tofind is not
   * a sub-array of tokens, it returns an empty list.
   * note that tokens sublist should have the exact elements and order as in tofind
   * @param tofind array you want to find in tokens
   * @param tokens array to search in
   * @param matchingFunction function that takes (tofindtoken, token) pair and returns whether they match
   * @return starting indices of the matching sublists
   */
  public static List<Integer> getSubListIndex(Object[] tofind, Object[] tokens, Predicate<Pair> matchingFunction){
    if(tofind.length > tokens.length)
      return null;
    List<Integer> allIndices = new ArrayList<>();
    boolean matched = false;
    int index = -1;
    int lastUnmatchedIndex = 0;
    for(int i = 0 ; i < tokens.length;){
      for(int j = 0; j < tofind.length ;){
        if(matchingFunction.test(new Pair(tofind[j], tokens[i]))){
          index = i;
          i++;
          j++;
          if(j == tofind.length)
          {
            matched = true;
            break;
          }
        }else{
          j = 0;
          i = lastUnmatchedIndex +1;
          lastUnmatchedIndex = i;
          index = -1;
          if(lastUnmatchedIndex == tokens.length)
            break;
        }
        if(i >= tokens.length){
          index = -1;
          break;
        }
      }
      if(i == tokens.length || matched){
        if(index >= 0)
          //index = index - l1.length + 1;
          allIndices.add(index - tofind.length + 1);
        matched = false;
        lastUnmatchedIndex = index;

        //break;
      }
    }
    //get starting point

    return allIndices;
  }

  /** Returns a new array which has the numbers in the input array
   *  L1-normalized.
   *
   *  @param ar Input array
   *  @return New array that has L1 normalized form of input array
   */
  public static double[] normalize(double[] ar) {
    double[] ar2 = new double[ar.length];
    double total = 0.0;
    for (double d : ar) {
      total += d;
    }
    for (int i = 0; i < ar.length; i++) {
      ar2[i] = ar[i]/total;
    }
    return ar2;
  }

  /**
   * Returns a new array, of the same component type, holding the elements of
   * {@code arr} from {@code startindexInclusive} to {@code endindexExclusive}.
   * Indices out of range are clipped to the array bounds; an empty array is
   * returned if the range is empty or starts past the end.
   *
   * @param arr The array to copy from
   * @param startindexInclusive Index of the first element to copy
   * @param endindexExclusive Index one past the last element to copy
   * @return The subarray, or null if {@code arr} is null
   */
  public static Object[] subArray(Object[] arr, int startindexInclusive, int endindexExclusive){
    if(arr == null)
      return arr;
    Class type = arr.getClass().getComponentType();
    if(endindexExclusive < startindexInclusive || startindexInclusive > arr.length -1 )
      return (Object[]) Array.newInstance(type, 0);
    if(endindexExclusive > arr.length)
      endindexExclusive = arr.length;
    if(startindexInclusive < 0)
      startindexInclusive = 0;
    Object[] b = (Object[]) Array.newInstance(type, endindexExclusive - startindexInclusive);
    System.arraycopy(arr, startindexInclusive, b, 0, endindexExclusive - startindexInclusive);
    return b;
  }

  /**
   * Compares two boolean arrays lexicographically, with false before true.
   * If one array is a prefix of the other, the shorter one comes first.
   *
   * @param a1 The first array
   * @param a2 The second array
   * @return A negative number, zero, or a positive number as {@code a1} is less than,
   *     equal to, or greater than {@code a2}
   */
  public static int compareBooleanArrays(boolean[] a1, boolean[] a2) {
    int len = Math.min(a1.length, a2.length);
    for (int i = 0; i < len; i++) {
      if (!a1[i] && a2[i]) return -1;
      if (a1[i] && !a2[i]) return 1;
    }
    // one is a prefix of the other, or they're identical
    if (a1.length < a2.length) return -1;
    if (a1.length > a2.length) return 1;
    return 0;
  }

  /**
   * Joins the values of the array into a string, separated by {@code glue}.
   *
   * @param doubles The values
   * @param glue The separator to put between values
   * @return The joined string (empty for an empty array)
   */
  public static String toString(double[] doubles, String glue) {
    String s = "";
    for(int i = 0; i < doubles.length; i++){
      if(i==0)
        s = String.valueOf(doubles[i]);
      else
        s+= glue + String.valueOf(doubles[i]);
    }
    return s;
  }
}
