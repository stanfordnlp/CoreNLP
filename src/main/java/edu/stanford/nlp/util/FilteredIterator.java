package edu.stanford.nlp.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Predicate;

/**
 * Iterator that suppresses items in another iterator based on a filter function.
 *
 * @author Dan Klein (klein@cs.stanford.edu)
 */
public class FilteredIterator<T> implements Iterator<T> {
  Iterator<T> iterator = null;
  Predicate<T> filter = null;
  T current = null;
  boolean hasCurrent = false;

  T currentCandidate() {
    return current;
  }

  void advanceCandidate() {
    if (!iterator.hasNext()) {
      hasCurrent = false;
      current = null;
      return;
    }
    hasCurrent = true;
    current = iterator.next();
  }

  boolean hasCurrentCandidate() {
    return hasCurrent;
  }

  boolean currentCandidateIsAcceptable() {
    return filter.test(currentCandidate());
  }

  void skipUnacceptableCandidates() {
    while (hasCurrentCandidate() && !currentCandidateIsAcceptable()) {
      advanceCandidate();
    }
  }

  public boolean hasNext() {
    return hasCurrentCandidate();
  }

  public T next() {
    T result = currentCandidate();
    advanceCandidate();
    skipUnacceptableCandidates();
    return result;
  }

  public void remove() {
    throw new UnsupportedOperationException();
  }

  public FilteredIterator(Iterator<T> iterator, Predicate<T> filter) {
    this.iterator = iterator;
    this.filter = filter;
    advanceCandidate();
    skipUnacceptableCandidates();
  }

  public static void main(String[] args) {
    Collection<String> c = Arrays.asList(new String[]{"a", "aa", "b", "bb", "cc"});
    Iterator<String> i = new FilteredIterator<>(c.iterator(), new Predicate<String>() {
      private static final long serialVersionUID = 1L;

      public boolean test(String o) {
        return o.length() == 1;
      }
    });
    while (i.hasNext()) {
      System.out.println("Accepted: " + i.next());
    }
  }
}
