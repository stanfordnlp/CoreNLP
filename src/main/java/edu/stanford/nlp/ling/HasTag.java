package edu.stanford.nlp.ling;

/**
 * Something that implements the <code>HasTag</code> interface
 * knows about part-of-speech tags.
 *
 * @author Christopher Manning
 */
public interface HasTag {

  /**
   * Return the tag value of the label (or null if none).
   *
   * @return String the tag value for the label
   */
  public String tag();


  /**
   * Set the tag value for the label (if one is stored).
   *
   * @param tag The tag value for the label
   */
  public void setTag(String tag);

}
