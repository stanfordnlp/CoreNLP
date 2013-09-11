package edu.stanford.nlp.sequences;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/**
 * This class represents the features and
 * values for each possible clique label
 * for a particular type 2 datum.  It is
 * just a Map, except that it checks if the
 * values array is the same for subsequently
 * added LabeledCliques, and if so it
 * reuses the array (to save memory).
 *
 * @author Jenny Finkel
 */

public class CliqueDatumType2 implements CliqueDatum {

  Map<LabeledClique, Features> featureMap = new HashMap<LabeledClique, Features>();
  
  public Features get(LabeledClique lc) {
    Features f = featureMap.get(lc);
    return f;
  }

  private float[] lastValues = null;
  
  public void setFeatures(LabeledClique lc, int[] features) {
    Features f = Features.valueOf(features);
    featureMap.put(lc, f);
  }

  public void setFeatures(LabeledClique lc, int[] features, float[] values) {
    if (lastValues != null && lastValues.length == values.length) {
      boolean match = true;
      for (int i = 0; i < values.length; i++) {
        if (values[i] != lastValues[i]) { match = false; break; }
      }
      if (match) { values = lastValues; }
    }
    Features f = Features.valueOf(features, values);
    featureMap.put(lc, f);
    lastValues = values;
  }


  public Set<LabeledClique> keySet() { return featureMap.keySet(); }
  
}
