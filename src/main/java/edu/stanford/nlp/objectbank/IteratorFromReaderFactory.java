package edu.stanford.nlp.objectbank;

import java.io.Serializable;
import java.util.Iterator;

/**
 * An IteratorFromReaderFactory is used to convert a java.io.Reader
 * into an Iterator over the Objects of type T represented by the text
 * in the java.io.Reader.
 *
 * (We have it be Serializable just to avoid non-serializable warnings;
 * since implementations of this class normally have no state, they
 * should be trivially serializable.)
 *
 * @author Jenny Finkel
 */
public interface IteratorFromReaderFactory<T> extends Serializable {

  /** Return an iterator over the contents read from r.
   *
   * @param r Where to read objects from
   * @return An Iterator over the objects
   */
  public Iterator<T> getIterator(java.io.Reader r);

}
