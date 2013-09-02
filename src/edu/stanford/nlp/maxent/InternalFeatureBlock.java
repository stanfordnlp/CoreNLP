package edu.stanford.nlp.maxent;

import edu.stanford.nlp.util.IntPair;

/**
 * This can have either small interger, float or other implememntations of
 * features.
 *
 * @author Kristina Toutanova
 */
interface InternalFeatureBlock {

  public int numFeatures();

  public int getFIndex(int position);

  public int numLocations();

  public float getFValue(int index);

  public IntPair location(int position);

}
