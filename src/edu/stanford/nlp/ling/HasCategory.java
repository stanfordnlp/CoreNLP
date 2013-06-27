package edu.stanford.nlp.ling;

/**
 * Something that implements the <code>HasCategory</code> interface
 * knows about categories.
 *
 * @author Christopher Manning
 */
public interface HasCategory {

  /**
   * Return the category value of the label (or null if none).
   *
   * @return String the category value for the label
   */
  public String category();


  /**
   * Set the category value for the label (if one is stored).
   *
   * @param category The category value for the label
   */
  public void setCategory(String category);

}
