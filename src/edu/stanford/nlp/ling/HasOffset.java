package edu.stanford.nlp.ling;

import java.io.Serializable;

/**
 * Something that implements the <code>HasOffset</code> interface
 * bears a offset reference to the original text
 *
 * @author Richard Eckart (Technische Universitat Darmstadt)
 */
public interface HasOffset extends Serializable {

  /**
   * Return the beginning character offset of the label (or -1 if none).
   *
   * @return the beginning position for the label
   */
  public int beginPosition();


  /**
   * Set the beginning character offset for the label.
   * Setting this key to "-1" can be used to
   * indicate no valid value.
   *
   * @param beginPos The beginning position
   */
  public void setBeginPosition(int beginPos);

  /**
   * Return the ending character offset of the label (or -1 if none).
   *
   * @return the end position for the label
   */
  public int endPosition();

  /**
   * Set the ending character offset of the label (or -1 if none).
   *
   * @param endPos The end character offset for the label
   */
  public void setEndPosition(int endPos);

}
