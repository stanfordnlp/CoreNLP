package edu.stanford.nlp.util;

import java.util.Iterator;

/**
 * Iterator with <code>remove()</code> defined to throw an
 * <code>UnsupportedOperationException</code>.
 */
abstract public class AbstractIterator<E> implements Iterator<E> {

  abstract public boolean hasNext();

  abstract public E next();

  /**
   * Throws an <code>UnupportedOperationException</code>.
   */
  public void remove() {
    throw new UnsupportedOperationException();
  }

}
