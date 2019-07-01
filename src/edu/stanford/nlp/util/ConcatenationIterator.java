package edu.stanford.nlp.util;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

/**
 * Iterator that represents the concatenation of two other iterators.
 * <br>
 * User: Dan Klein (klein@cs.stanford.edu)
 * Date: Oct 22, 2003
 * Time: 7:27:39 PM
 */
public class ConcatenationIterator<T> implements Iterator<T> {
  Iterator<T> first = null;
  Iterator<T> second = null;

  Iterator<T> current() {
    if (first.hasNext()) {
      return first;
    }
    return second;
  }

  public boolean hasNext() {
    return current().hasNext();
  }

  public T next() {
    return current().next();
  }

  public void remove() {
    current().remove();
  }

  public ConcatenationIterator(Iterator<T> first, Iterator<T> second) {
    this.first = first;
    this.second = second;
  }

  public static void main(String[] args) {
    Collection<String> c1 = Collections.singleton("a");
    Collection<String> c2 = Collections.singleton("b");
    Iterator<String> i = new ConcatenationIterator<>(c1.iterator(), c2.iterator());
    while (i.hasNext()) {
      System.out.println(i.next() + " ");
    }
  }
}

