package edu.stanford.nlp.util;

import java.util.Arrays;

/**
 * Simple wrapper around an array of int, which overrides hashCode() and equals()
 * of Object. This class is useful if used as a key in a HashMap, Counter, etc.
 *
 * @author Michel Galley
 */

public class IntArray {

  private final int[] array;

  /**
   * Wraps the given array.  The array is not copied.
   *
   * @param array The array to wrap
   */
  public IntArray(int[] array) { 
    this.array = array; 
  }

  /**
   * Returns the wrapped array itself, not a copy.
   *
   * @return The wrapped array
   */
  public int[] get() {
    return array;
  }

  @Override
  public int hashCode() { 
    return Arrays.hashCode(array); 
  }

  @Override
  public boolean equals(Object o) { 
    return Arrays.equals(array,((IntArray)o).array); 
  }

}
