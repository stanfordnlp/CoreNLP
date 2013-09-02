package edu.stanford.nlp.ie.hmm;

import edu.stanford.nlp.ie.TypedTaggedDocument;
import edu.stanford.nlp.util.AbstractIterator;

/**
 * Superclass for Iterators that are designed to iterate over information
 * extraction document collections. The hard requirement is to provide a
 * <tt>getTargetFields</tt> method. The soft requirement is that
 * <tt>readDocument</tt> should return a TypedTaggedDocument.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 * @author Huy Nguyen (htnguyen@cs.stanford.edu)
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 */
public abstract class IECollectionIterator<T> extends AbstractIterator<T> //extends DocumentReader
{
  protected String[] targetFields;

  /**
   * Uses the given target fields (adds BG if needed).
   */
  public void setTargetFields(String[] targetFields) {
    this.targetFields = TypedTaggedDocument.getTargetFieldsPlusBG(targetFields);
  }

  /**
   * Returns the list of target fields, including (Background) being handled
   * by this IECollectionIterator.
   */
  public String[] getTargetFields() {
    return (targetFields);
  }
}
