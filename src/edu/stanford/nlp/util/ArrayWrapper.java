package edu.stanford.nlp.util;

import java.util.Arrays;

/**
 * Wraps an array. The only advantage of this class is that hashCode() hashes
 * the elements of the array.
 * 
 * @author Michel Galley
 */
public class ArrayWrapper<E> {

  E[] arr;

  public ArrayWrapper(E ... arr) {
    this.arr = arr;
  }

  /**
   * StringArrays are equal if they contain the same elements.
   */
  @SuppressWarnings("unchecked")
  public boolean equals(Object o) {
    assert(o instanceof ArrayWrapper);
    ArrayWrapper aw = (ArrayWrapper) o;
    return Arrays.equals(arr,aw.arr);
  }

  public int hashCode() {
    return Arrays.hashCode(arr);
  }
}
