package edu.stanford.nlp.util;

import java.util.*;


/** Provides permutations of the integers 0to n-1.
 *  See {@link PermutationGenerator} for details.
 *  This class exists to support using it with enhanced for loop syntax.
 *
 *  @author Christopher Manning
 */
public class Permutation implements Iterable<int[]> {

  private int n;

  /** Create a permutation of integers up to a certain size.
   *
   *  @param n The size of the list of integers
   */
  public Permutation(int n) {
    this.n = n;
  }

  public Iterator<int[]> iterator() {
    return new PermutationGenerator(n);
  }

}
