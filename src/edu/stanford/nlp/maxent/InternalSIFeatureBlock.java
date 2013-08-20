package edu.stanford.nlp.maxent;

import edu.stanford.nlp.util.IntPair;

/**
 * The internal representation of a feature block
 * when the values are small integers.
 *
 * @author Kristina Toutanova
 */
class InternalSIFeatureBlock implements InternalFeatureBlock {

  int[] inputFIndices;
  IntPair[] locations; // in which pairs of labelIndex alternative is the feature on

  public int numFeatures() {
    return inputFIndices.length;
  }

  public int getFIndex(int position) {
    return inputFIndices[position];
  }

  public float getFValue(int index) {
    return 1;
  }

  public int numLocations() {
    return locations.length;
  }

  public IntPair location(int position) {
    return locations[position];
  }

}
