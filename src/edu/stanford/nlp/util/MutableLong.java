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
    return (int)(i ^ (i >>> 32));
  }

  /**
   * Compares this object to the specified object.  The result is
   * {@code true} if and only if the argument is not
   * {@code null} and is an {@code MutableLong} object that
   * contains the same {@code long} value as this object.
   * Note that a MutableLong isn't and can't be equal to an Long.
   *
   * @param obj the object to compare with.
   * @return {@code true} if the objects are the same;
   *         {@code false} otherwise.
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    } else if (obj instanceof MutableLong) {
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
  @Override
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

  /** Add the argument to the value of this long.  A convenience method.
   *
   * @param val Value to be added to this long
   */
  public void incValue(long val) {
    i += val;
  }

  public MutableLong() {
    this(0);
  }

  public MutableLong(long i) {
    this.i = i;
  }

  private static final long serialVersionUID = 624465615824626762L;

}
