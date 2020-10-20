package edu.stanford.nlp.ling;

import java.io.Serializable;

/**
 * Something that implements the <code>HasWord</code> interface
 * knows about words.
 *
 * @author Christopher Manning
 */
public interface HasWord extends Serializable {

  /**
   * Return the word value of the label (or null if none).
   *
   * @return String the word value for the label
   */
  public String word();


  /**
   * Set the word value for the label (if one is stored).
   *
   * @param word The word value for the label
   */
  public void setWord(String word);

}
