package edu.stanford.nlp.ling;

/**
 * Something that implements the <code>HasType</code> interface
 * knows about HMM target types.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public interface HasType {
  /**
   * Return the type value of the label (or null if none).
   *
   * @return int the type value for the label
   */
  public int type();


  /**
   * Set the type value for the label (if one is stored).
   *
   * @param type The type value for the label
   */
  public void setType(int type);
}

