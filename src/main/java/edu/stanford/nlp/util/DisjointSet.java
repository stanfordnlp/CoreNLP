package edu.stanford.nlp.util;

/**
 * Disjoint set interface.
 *
 * @author Dan Klein
 * @version 4/17/01
 */
public interface DisjointSet<T> {
  public T find(T o);

  public void union(T a, T b);
}
