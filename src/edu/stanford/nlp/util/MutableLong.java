package edu.stanford.nlp.util;

/**
 * A class for Long objects that you can change.
 *
 * @author Dan Klein
 */
public final class MutableLong extends Number implements Comparable<MutableLong> {

  private long i;

  // Mutable
  public void set(long i) {
    this.i = i;
  }

  @Override
  public int hashCode() {
    return (int) i;
  }

  /**
   * Compares this object to the specified object.  The result is
   * <code>true</code> if and only if the argument is not
   * <code>null</code> and is an <code>MutableInteger</code> object that
   * contains the same <code>int</code> value as this object.
   * Note that a MutableInteger isn't and can't be equal to an Integer.
   *
   * @param obj the object to compare with.
   * @return <code>true</code> if the objects are the same;
   *         <code>false</code> otherwise.
   */
  @Override
  public boolean equals(Object obj) {
    if (obj instanceof MutableLong) {
      return i == ((MutableLong) obj).i;
    }
    return false;
  }

  @Override
  public String toString() {
    return Long.toString(i);
  }

  // Comparable interface

  /**
   * Compares two <code>MutableLong</code> objects numerically.
   *
   * @param anotherMutableLong the <code>MutableLong</code> to be
   *                              compared.
   * @return The value <code>0</code> if this <code>MutableLong</code> is
   *         equal to the argument <code>MutableLong</code>; a value less than
   *         <code>0</code> if this <code>MutableLong</code> is numerically less
   *         than the argument <code>MutableLong</code>; and a value greater
   *         than <code>0</code> if this <code>MutableLong</code> is numerically
   *         greater than the argument <code>MutableLong</code> (signed
   *         comparison).
   */
  public int compareTo(MutableLong anotherMutableLong) {
    long thisVal = this.i;
    long anotherVal = anotherMutableLong.i;
    return (thisVal < anotherVal ? -1 : (thisVal == anotherVal ? 0 : 1));
  }


  // Number interface
  @Override
  public int intValue() {
    return (int) i;
  }

  @Override
  public long longValue() {
    return i;
  }

  @Override
  public short shortValue() {
    return (short) i;
  }

  @Override
  public byte byteValue() {
    return (byte) i;
  }

  @Override
  public float floatValue() {
    return i;
  }

  @Override
  public double doubleValue() {
    return i;
  }

  /** Add the argument to the value of this integer.  A convenience method.
   *
   * @param val Value to be added to this integer
   */
  public void incValue(int val) {
    i += val;
  }

  public MutableLong() {
    this(0);
  }

  public MutableLong(int i) {
    this.i = i;
  }

  private static final long serialVersionUID = 624465615824626762L;
}
