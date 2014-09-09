package edu.stanford.nlp.ling;

import java.io.Serializable;

/**
 * Something that implements the <code>HasLemma</code> interface
 * knows about lemmas.
 *
 * @author John Bauer
 */
public interface HasLemma extends Serializable {

  /**
   * Return the lemma value of the label (or null if none).
   *
   * @return String the lemma value for the label
   */
  public String lemma();


  /**
   * Set the lemma value for the label (if one is stored).
   *
   * @param lemma The lemma value for the label
   */
  public void setLemma(String lemma);

}
