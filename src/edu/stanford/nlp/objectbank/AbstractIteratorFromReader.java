package edu.stanford.nlp.objectbank;

import edu.stanford.nlp.util.AbstractIterator;

import java.io.Reader;

/**
 * An abstract superclass for all Iterators created from Readers.  These are the class of Iterators vended
 * by {@link IteratorFromReaderFactory}s.  Subclasses must implement the {@link java.util.Iterator#hasNext hasNext}
 * and {@link java.util.Iterator#next next} methods.
 *
 * @author Huy Nguyen (htnguyen@cs.stanford.edu)
 */
public abstract class AbstractIteratorFromReader<T> extends AbstractIterator<T> {
  protected Reader reader;

  public AbstractIteratorFromReader(Reader reader) {
    this.reader = reader;
  }
}
