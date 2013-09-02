package edu.stanford.nlp.util;

import java.util.Comparator;

/**
 * Comparator that says every pair of objects is tied.
 *
 * @author Dan Klein (klein@cs.stanford.edu)
 */
public class NullComparator<T> implements Comparator<T> {
  public int compare(T a, T b) {
    return 0;
  }
}
