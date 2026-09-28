package edu.stanford.nlp.util;

import java.io.Serializable;
import java.util.List;


/**
 * A tuple of int. There are special classes for IntUni, IntPair, IntTriple
 * and IntQuadruple. The motivation for that was the different hashCode
 * implementations.
 * By using the static IntTuple.getIntTuple(numElements) one can obtain an
 * instance of the appropriate sub-class.
 *
 * @author Kristina Toutanova (kristina@cs.stanford.edu)
 */
public class IntTuple implements Serializable, Comparable<IntTuple> {

  /** The elements of the tuple. */
  final int[] elements;

  private static final long serialVersionUID = 7266305463893511982L;


  /**
   * Creates a tuple backed by the given array.  The array is not copied.
   *
   * @param arr The elements of the tuple
   */
  public IntTuple(int[] arr) {
    elements = arr;
  }

  /**
   * Creates a tuple of {@code num} zeros.
   *
   * @param num The length of the tuple
   */
  public IntTuple(int num) {
    elements = new int[num];
  }

  @Override
  public int compareTo(IntTuple o) {
    int commonLen = Math.min(o.length(), length());
    for (int i = 0; i < commonLen; i++) {
      int a = get(i);
      int b = o.get(i);
      if (a < b) return -1;
      if (b < a) return 1;
    }
    if (o.length() == length()) {
      return 0;
    } else {
      return (length() < o.length())? -1:1;
    }
  }

  /**
   * Returns one element of the tuple.
   *
   * @param num The index of the element
   * @return The element at index {@code num}
   */
  public int get(int num) {
    return elements[num];
  }


  /**
   * Sets one element of the tuple.
   *
   * @param num The index of the element
   * @param val The new value
   */
  public void set(int num, int val) {
    elements[num] = val;
  }

  /**
   * Shifts every element one position toward the start, dropping the first
   * element and setting the last element to 0.
   */
  public void shiftLeft() {
    System.arraycopy(elements, 1, elements, 0, elements.length - 1);  // the API does guarantee that this works when src and dest overlap, as here
    elements[elements.length - 1] = 0;
  }


  /**
   * Returns a copy of this tuple, using the subclass chosen by {@link #getIntTuple(int)}.
   *
   * @return A new tuple with the same elements
   */
  public IntTuple getCopy() {
    IntTuple copy = IntTuple.getIntTuple(elements.length); //new IntTuple(numElements);
    System.arraycopy(elements, 0, copy.elements, 0, elements.length);
    return copy;
  }


  /**
   * Returns the array backing this tuple (not a copy).
   *
   * @return The elements of the tuple
   */
  public int[] elems() {
    return elements;
  }

  @Override
  public boolean equals(Object iO) {
    if (!(iO instanceof IntTuple)) {
      return false;
    }
    IntTuple i = (IntTuple) iO;
    if (i.elements.length != elements.length) {
      return false;
    }
    for (int j = 0; j < elements.length; j++) {
      if (elements[j] != i.get(j)) {
        return false;
      }
    }
    return true;
  }


  @Override
  public int hashCode() {
    int sum = 0;
    for (int element : elements) {
      sum = sum * 17 + element;
    }
    return sum;
  }


  /**
   * Returns the number of elements in the tuple.
   *
   * @return The length of the tuple
   */
  public int length() {
    return elements.length;
  }


  /**
   * Returns a new all-zero tuple of the given length, using {@link IntUni},
   * {@link IntPair}, {@link IntTriple}, or {@link IntQuadruple} for lengths 1 to 4.
   *
   * @param num The length of the tuple
   * @return A new tuple of length {@code num}
   */
  public static IntTuple getIntTuple(int num) {
    if (num == 1) {
      return new IntUni();
    }
    if ((num == 2)) {
      return new IntPair();
    }
    if (num == 3) {
      return new IntTriple();
    }
    if (num == 4) {
      return new IntQuadruple();
    } else {
      return new IntTuple(num);
    }
  }


  /**
   * Returns a new tuple containing the given integers, using the subclass chosen by
   * {@link #getIntTuple(int)}.
   *
   * @param integers The elements of the tuple
   * @return A new tuple with the same elements as {@code integers}
   */
  public static IntTuple getIntTuple(List<Integer> integers) {
    IntTuple t = IntTuple.getIntTuple(integers.size());
    for (int i = 0; i < t.length(); i++) {
      t.set(i, integers.get(i).intValue());
    }
    return t;
  }

  @Override
  public String toString() {
    StringBuilder name = new StringBuilder();
    for (int i = 0; i < elements.length; i++) {
      name.append(get(i));
      if (i < elements.length - 1) {
        name.append(' ');
      }
    }
    return name.toString();
  }


  /**
   * Concatenates two tuples into a new tuple.
   *
   * @param t1 The first tuple
   * @param t2 The second tuple
   * @return A new tuple with the elements of {@code t1} followed by those of {@code t2}
   */
  public static IntTuple concat(IntTuple t1, IntTuple t2) {
    int n1 = t1.length();
    int n2 = t2.length();
    IntTuple res = IntTuple.getIntTuple(n1 + n2);

    for (int j = 0; j < n1; j++) {
      res.set(j, t1.get(j));
    }
    for (int i = 0; i < n2; i++) {
      res.set(n1 + i, t2.get(i));
    }
    return res;
  }


  /**
   * Prints {@link #toString()} to {@code System.out}, with no newline.
   */
  public void print() {
    String s = toString();
    System.out.print(s);
  }

}
