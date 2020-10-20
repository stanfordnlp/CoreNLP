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

  public IntArray(int[] array) { 
    this.array = array; 
  }

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
