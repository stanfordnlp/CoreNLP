package edu.stanford.nlp.util;

import java.util.Iterator;

/**
 * Iterator with <code>remove()</code> defined to throw an
 * <code>UnsupportedOperationException</code>.
 *
 * @param <E> The type of the elements returned by this iterator
 */
public abstract class AbstractIterator<E> implements Iterator<E> {

  /** Constructor for subclasses. */
  public AbstractIterator() { }

  /** {@inheritDoc} */
  @Override
  public abstract boolean hasNext();

  /** {@inheritDoc} */
  @SuppressWarnings("IteratorNextCanNotThrowNoSuchElementException")
  @Override
  public abstract E next();

  /**
   * Throws an <code>UnsupportedOperationException</code>.
   */
  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }

}
