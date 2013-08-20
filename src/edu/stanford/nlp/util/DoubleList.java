package edu.stanford.nlp.util;

/**
 * Sort of like a {@link java.util.List} but contains and returns primitive 
 * doubles.  Backed by an array so arbitrary-position insertion is expensive,
 * but insertion at the end of the list is fast, and access is fast.
 *
 * @author Roger Levy
 */
public class DoubleList {

  private double[] doubles;
  private int size = 0;

  public DoubleList() {
    this(10);
  }

  public DoubleList(int initialSize) {
    doubles = new double[initialSize];
  }

  public void clear() {
    size = 0;
  }

  /**
   * inserts d at the end of the list.
   */
  public void add(double d) {
    add(size, d);
  }

  /**
   * Inserts d into the list after the first <i>index</i> elements.
   */
  public void add(int index, double d) throws IndexOutOfBoundsException {
    if (index > size) {
      throw new IndexOutOfBoundsException("Error -- can't get position " + index + " from a DoubleList of size " + size);
    }
    ensureSize();
    System.arraycopy(doubles, index, doubles, index + 1, size - index);
    doubles[index] = d;
    size++;
  }

  private void ensureSize() {
    if (size == doubles.length) {
      double[] newDoubles = new double[size * 2];
      System.arraycopy(doubles, 0, newDoubles, 0, size);
      doubles = newDoubles;
    }
  }

  public int size() {
    return size;
  }

  /**
   * gets the value of the <i>index</i>-th element of the list.
   */
  public double get(int index) throws IndexOutOfBoundsException {
    if (index >= size) {
      throw new IndexOutOfBoundsException("Error -- can't get position " + index + " from a DoubleList of size " + size);
    }
    return doubles[index];
  }

  /**
   * Returns a safe copy of the original array.
   */
  public double[] toArray() {
    double[] result = new double[size];
    System.arraycopy(doubles, 0, result, 0, size);
    return result;
  }
  
  /**
   * Returns the raw array, not all of which may be filled.  Use with caution!  The method {@link #size()} can be used to determine
   * how many entries are filled.
   * @return the raw array of doubles
   */
  public double[] rawArray() {
    return doubles;
  }

  /**
   * returns true iff the list contains no elements.
   */
  public boolean isEmpty() {
    return size == 0;
  }

  /**
   * sets the index-th element of the list to d, and returns the old value of the index-th element.
   */
  public double set(int index, double d) throws IndexOutOfBoundsException {
    if (index >= size) {
      throw new IndexOutOfBoundsException("Error -- can't get position " + index + " from a DoubleList of size " + size);
    }
    double d1 = doubles[index];
    doubles[index] = d;
    return d1;
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder("(");
    if (size > 0) {
      result.append(get(0));
    }
    for (int i = 1; i < size; i++) {
      result.append(',');
      result.append(doubles[i]);
    }
    result.append(')');
    return result.toString();
  }

  public static void main(String[] args) {
    DoubleList dl = new DoubleList();
    dl.add(3.0);
    dl.add(4.0);
    dl.add(1, 2.5);
    double[] doubles = dl.toArray();
    for (double d : doubles) {
      System.out.println(d);
    }
    System.out.println(dl.toString());

  }

}
