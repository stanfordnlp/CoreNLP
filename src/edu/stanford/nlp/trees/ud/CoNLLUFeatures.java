package edu.stanford.nlp.trees.ud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A subclass of TreeMap with a toString() that looks like a CoNLLUFeatures
 * and a method for extracting the features from a CoNLLU string
 * <br>
 * This is a TreeMap so that the features are sorted by their key,
 * which is necessary for the CoNLLU format
 */
public class CoNLLUFeatures extends TreeMap<String, String> {
  public static class LowercaseComparator implements Comparator<String> {
    public int compare(String x, String y) {
      if (x == null && y == null) {
        return 0;
      }
      if (x == null) {
        return -1;
      }
      if (y == null) {
        return 1;
      }
      return x.compareToIgnoreCase(y);
    }
  }

  static final LowercaseComparator comparator = new LowercaseComparator();

  /**
   * Parses the value of the feature column in a CoNLL-U file
   * and returns them in a HashMap with the feature names as keys
   * and the feature values as values.
   *
   * @param featureString
   * @return A {@code HashMap<String,String>} with the feature values.
   */
  public CoNLLUFeatures(String featureString) {
    super(comparator);

    if (!featureString.equals("_")) {
      String[] featValPairs = featureString.split("\\|");
      for (String p : featValPairs) {
        String[] featValPair = p.split("=");
        this.put(featValPair[0], featValPair[1]);
      }
    }
  }

  public CoNLLUFeatures(Map<String, String> features) {
    super(comparator);
    putAll(features);
  }

  public CoNLLUFeatures() {
    super(comparator);
  }


  public static class FeatureNameComparator implements Comparator<String> {
    @Override
    public int compare(String featureName1, String featureName2) {
      return featureName1.toLowerCase().compareTo(featureName2.toLowerCase());
    }
  }

  /**
   * Converts the features to a feature string to be used
   * in a CoNLL-U file.
   *
   * @return The feature string.
   */
  public static String toFeatureString(Map<String,String> features) {
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    if (features != null) {
      List<String> sortedKeys = new ArrayList<>(features.keySet());
      Collections.sort(sortedKeys, new FeatureNameComparator());
      for (String key : sortedKeys) {
        if (!first) {
          sb.append("|");
        } else {
          first = false;
        }
        sb.append(key)
          .append("=")
          .append(features.get(key));
        
      }
    }
    /* Empty feature list. */
    if (first) {
      sb.append("_");
    }

    return sb.toString();
  }

  public String toString() {
    return toFeatureString(this);
  }
}
