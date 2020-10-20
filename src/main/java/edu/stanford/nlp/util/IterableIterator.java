package edu.stanford.nlp.util;

import java.util.*;
import java.util.stream.Stream;

/**
 * This cures a pet peeve of mine: that you can't use an Iterator directly in
 * Java 5's foreach construct.  Well, this one you can, dammit.
 *
 * @author Bill MacCartney
 */
public class IterableIterator<E> implements Iterator<E>, Iterable<E> {

  private Iterator<E> it;
  private Iterable<E> iterable;
  private Stream<E> stream;

  public IterableIterator(Iterator<E> it) {
    this.it = it;
  }

  public IterableIterator(Iterable<E> iterable) {
    this.iterable = iterable;
    this.it = iterable.iterator();
  }

  public IterableIterator(Stream<E> stream) {
    this.stream = stream;
    this.it = stream.iterator();
  }

  public boolean hasNext() { return it.hasNext(); }
  public E next() { return it.next(); }
  public void remove() { it.remove(); }
  
  public Iterator<E> iterator() {
    if (iterable != null) {
      return iterable.iterator();
    } else if (stream != null) {
      return stream.iterator();
    } else {
      return this;
    }
  }

  @Override
  public Spliterator<E> spliterator() {
    if (iterable != null) {
      return iterable.spliterator();
    } else if (stream != null) {
      return stream.spliterator();
    } else {
      return Spliterators.spliteratorUnknownSize(it, Spliterator.ORDERED | Spliterator.CONCURRENT);
    }
  }
}
