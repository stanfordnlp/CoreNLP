package edu.stanford.nlp.process;


import java.io.Serializable;

/**
 * This provides an interface for a feature that can be used
 * to define a partition over the space of possible unseen words.
 * A Feature assigns a value to an unseen word, selected from the
 * set of possible FeatureValue. An implementation of this class
 * must also specify an implementation of the FeatureValue interface.
 *
 * @author Teg Grenager grenager@cs.stanford.edu
 */
public interface Feature extends Serializable {

  /**
   * The number of possible FeatureValues for this Feature.
   */
  public int numValues();

  /**
   * An array of all possible FeatureValues for this Feature.
   */
  public FeatureValue[] allValues();

  /**
   * This returns the FeatureValue of the String s for this Feature.
   */
  public FeatureValue getValue(String s);


}
