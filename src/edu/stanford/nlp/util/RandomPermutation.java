package edu.stanford.nlp.util;

import java.util.Random;

public class RandomPermutation {
  private RandomPermutation() {} // static methods only

  /**
   * Creates a randomized permutation of the integers from 0 (inclusive)
   * to n (exclusive).  Useful, e.g. to randomize the order or access
   * to array elements.
   */
  public static int[] randomPermutation(Random random, int n) {
    int[] arr = new int[n];
    for (int i = 0; i < n; i++) {
      arr[i] = i;
    }
    for (int i = n; 0 < i; i--) {
      swap(arr, i - 1, random.nextInt(i));
    }
    return arr;
  }


  /**
   * Swaps two elements
   */
  private static final void swap(int[] arr, int s, int t) {
    int tmp = arr[s];
    arr[s] = arr[t];
    arr[t] = tmp;
  }

}