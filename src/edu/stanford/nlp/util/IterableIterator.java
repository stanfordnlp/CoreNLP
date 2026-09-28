package edu.stanford.nlp.util;

import java.util.*;
import java.util.stream.Stream;

/**
 * This cures a pet peeve of mine: that you can't use an Iterator directly in
 * Java 5's foreach construct.  Well, this one you can, dammit.
 *
 * @param <E> the type of elements returned
 * @author Bill MacCartney
 */
public class IterableIterator<E> implements Iterator<E>, Iterable<E> {

  private Iterator<E> it;
  private Iterable<E> iterable;
  private Stream<E> stream;

  /**
   * Wraps an iterator.  {@link #iterator()} returns this object, so the
   * elements can only be iterated over once.
   *
   * @param it the iterator to wrap
   */
  public IterableIterator(Iterator<E> it) {
    this.it = it;
  }

  /**
   * Wraps an iterable.  This object iterates over one iterator of
   * {@code iterable}, while {@link #iterator()} returns a fresh one.
   *
   * @param iterable the iterable to wrap
   */
  public IterableIterator(Iterable<E> iterable) {
    this.iterable = iterable;
    this.it = iterable.iterator();
  }

  /**
   * Wraps a stream, taking its iterator immediately.  Since that consumes
   * the stream, {@link #iterator()} and {@link #spliterator()} throw
   * {@code IllegalStateException} for an object built this way.
   *
   * @param stream the stream to wrap
   */
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
