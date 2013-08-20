
package edu.stanford.nlp.sequences;

import java.util.List;

/**
 * This class represents the features and
 * values for each possible clique label
 * for a particular <b>TYPE 1</b> datum
 * which lives in a type 2 world.  By this
 * i mean that it for different LabeledCliques
 * it will return different indices for the
 * same feature, but that those indices will
 * just refer to the [Feature,LabeledClique]
 * pair.  It is memory efficient, but not
 * time efficient, because it will recalculate
 * the feature array each time.
 *
 * @author Jenny Finkel
 */

public class CliqueDatumType1 implements CliqueDatum {

  FeatureMap featureMap;
  List<LabeledClique> keySet;
  
  public CliqueDatumType1(FeatureMap fm, List<LabeledClique> keySet) {
    featureMap = fm;
    this.keySet = keySet;
  }

  public List<LabeledClique> keySet() { return keySet; }
  
  public Features get(LabeledClique lc) {
    if (lc == origLC) {
      return (values == null ? Features.valueOf(features) : Features.valueOf(features, values));
    }
    int[] f = featureMap.getFeatureArray(features, lc);
    return (values == null ? Features.valueOf(f) : Features.valueOf(f, values));
  }

  private LabeledClique origLC = null;
  private int[] features = null;
  private float[] values = null;
  
  public void setFeatures(LabeledClique lc, int[] features) {
    if (this.features == null) {
      this.features = features;
      origLC = lc;
    }
    int i = 0;
    for (int f : this.features) {
      featureMap.setFeatureIndex(f,lc, features[i]);
      i++;
    }
  }

  public void setFeatures(LabeledClique lc, int[] features, float[] values) {
    if (this.features == null) {
      this.features = features;
      this.values = values;
      origLC = lc;
    }
    int i = 0;
    for (int f : this.features) {
      featureMap.setFeatureIndex(f,lc, features[i]);
      i++;
    }
  }
  
}
