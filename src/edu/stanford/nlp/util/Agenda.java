package edu.stanford.nlp.util;

/**
 * Interface for representing an agenda (a list of things to
 * be done).
 *
 * @author Dan Klein
 * @version 12/4/00
 */
public interface Agenda<T> {

  void add(T o);

  /**
   * Return the next item from the Agenda, and delete it from the Agenda.
   * <i>(Note how this is rather different to the Collections
   * <code>next()</code> operation!)</i>
   *
   * @return The next item from the Agenda.
   */
  T next();

  boolean hasNext();
  
}
