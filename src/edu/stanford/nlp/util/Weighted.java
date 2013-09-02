package edu.stanford.nlp.util;

/**
 * Weighted. An interface for representing a weighted things, such as
 * a feature (as opposed to a probability/score).
 *
 * @author Christopher Manning
 * @version 2002/06/12
 */
public interface Weighted {

  /**
   * Return the weight of this thing
   *
   * @return the weight
   */
  public double weight();

  /**
   * Set the weight of this thing
   *
   * @param weight the weight
   */
  public void setWeight(double weight);

}

