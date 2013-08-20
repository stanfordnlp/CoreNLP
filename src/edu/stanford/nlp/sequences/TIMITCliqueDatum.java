package edu.stanford.nlp.sequences;

import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

/**
 * This class represents the features and
 * values for a datum for the timit dataset.
 * it is specially optimized for data where
 * the same features are present in all datums
 * (but with different values) and if you try to
 * use it on data that looks differently, it won't
 * be pretty.
 *
 * @author Jenny Finkel
 */

public class TIMITCliqueDatum implements CliqueDatum, java.io.Serializable {

  /**
   * 
   */
  private static final long serialVersionUID = 1948440233285748714L;
  public static Map<LabeledClique, int[]> featureMap = new HashMap<LabeledClique, int[]>();
  private Set<LabeledClique> keySet = null;

  /**
   * The parameter tells it whether or not to make its own keySet.
   * If not, it will return all possible labels.  it will always
   * be correct if you set this to true, but super wasteful of
   * memory.
   */
  public TIMITCliqueDatum(boolean makeKeySet) {
    if (makeKeySet) { keySet = new HashSet<LabeledClique>(); }
  }
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("TIMITCliqueDatum: ");
    sb.append("\n\tvalues: ");
    if (values == null) {
      sb.append("null");
    } else {
      for (int i=0; i<values.length && i<5; i++) {
        sb.append(values[i]+" ");
      }
    }
    sb.append("\n\tfeatureMap: " + featureMap);
    return sb.toString();
  }
  
  public Set<LabeledClique> keySet() {
    if (keySet == null) { return featureMap.keySet(); }
    else { return keySet; }
  }
  
  public Features get(LabeledClique lc) {
    int[] f = featureMap.get(lc);
    return Features.valueOf(f, values);
  }

  private float[] values = null;
  
  public void setFeatures(LabeledClique lc, int[] features) {
    throw new RuntimeException("You must specify values!");
  }

  public void setFeatures(LabeledClique lc, int[] features, float[] values) {
    System.err.println("created");
    featureMap.put(lc, features);
    if (keySet != null) {
      keySet.add(lc);
    }
    if (this.values != null) {
      for (int i = 0; i < this.values.length; i++) {
        if (this.values[i] != values[i]) {
          throw new RuntimeException("values arrays must match!"); 
        }
      }
    }
    this.values = values;
  }
}
