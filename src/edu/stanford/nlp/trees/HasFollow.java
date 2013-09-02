package edu.stanford.nlp.trees;

/**
 * Something that implements the <code>HasFollow</code> interface
 * knows about the characters that follow a token.
 *
 * @author Christopher Manning
 */
public interface HasFollow {

  /**
   * Return the follow value of the label (or null if none).
   *
   * @return String the follow value for the label
   */
  public String follow();


  /**
   * Set the follow value for the label (if one is stored).
   *
   * @param follow The follow value for the label
   */
  public void setFollow(String follow);

}
