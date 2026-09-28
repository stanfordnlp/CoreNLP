package edu.stanford.nlp.util;

/**
 * Disjoint set interface.
 *
 * @author Dan Klein
 * @version 4/17/01
 * @param <T> the type of the set elements
 */
public interface DisjointSet<T> {
  /**
   * Finds the representative element of the set containing the given element.
   *
   * @param o an element
   * @return the representative of {@code o}'s set
   */
  public T find(T o);

  /**
   * Merges the sets containing the two given elements.
   *
   * @param a an element of the first set
   * @param b an element of the second set
   */
  public void union(T a, T b);
}
