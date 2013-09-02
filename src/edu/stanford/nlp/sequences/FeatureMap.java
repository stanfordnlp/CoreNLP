package edu.stanford.nlp.sequences;

import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.HashIndex;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.io.Serializable;

/**
 * This class is designed to assist CliqueDatumType1s.
 * It is a map, from a feature index and LabeledClique
 * to a new feature index.  For all feature indexes
 * which correspond to the same feature, regardless
 * of the label, return values will be the same.
 * There is a probably a better way to explain this.
 *
 * @author Jenny Finkel
 */

public class FeatureMap implements Serializable {

  protected static final long serialVersionUID = 3160710775400396861l;
  
  DatasetMetaInfo metaInfo;
  List<Map<LabeledClique, Integer>> slowMap = new ArrayList<Map<LabeledClique, Integer>>();
  HashIndex<LabeledClique> lcIndex = new HashIndex<LabeledClique>();
  private int[][] fastMap = null;
  
  public FeatureMap(DatasetMetaInfo metaInfo) {
    this.metaInfo = metaInfo;
  }

  public int[] getFeatureArray(int[] origFeatureArray, LabeledClique lc) {
    int[] newFeatureArray = new int[origFeatureArray.length];
    if (fastMap != null) {
      int lci = lcIndex.indexOf(lc);
      for (int i = 0; i < origFeatureArray.length; i++) {
        newFeatureArray[i] = fastMap[origFeatureArray[i]][lci];
      }
    } else {
      for (int i = 0; i < origFeatureArray.length; i++) {
        Integer j = slowMap.get(origFeatureArray[i]).get(lc);
        if (j == null) {
          throw new RuntimeException("could not find feature!");
        }
        newFeatureArray[i] = j;
      }
    }
    return newFeatureArray;
  }

  public void setFeatureIndex(int origFeatureIndex, LabeledClique lc, int newFeatureIndex) {
    if (fastMap != null) {
      if (fastMap.length < origFeatureIndex+1 || fastMap.length < newFeatureIndex+1) {
        throw new RuntimeException("You can't add more features now that youve made this faster");
      }
      return;
    }

    while (slowMap.size() < origFeatureIndex+1 || slowMap.size() < newFeatureIndex+1) {
      slowMap.add(null);
    }
    Map m = slowMap.get(origFeatureIndex);
    if (m == null) {
      m = slowMap.get(newFeatureIndex);
      if (m == null) {
        m = new HashMap<LabeledClique, Integer>();
      }
    }
    m.put(lc, newFeatureIndex);
    slowMap.set(newFeatureIndex, m);
    slowMap.set(origFeatureIndex, m);
    lcIndex.add(lc);
  }

  private Set keySet = null;
  public Set<LabeledClique> keySet() {
    if (keySet == null) { keySet =  new HashSet(lcIndex); }
    return keySet;
  }

  public void makeFaster() {
    if (fastMap != null) { return; }
    fastMap = new int[slowMap.size()][];
    for (int i = 0; i < fastMap.length; i++) {
      if (fastMap[i] == null) {
        fastMap[i] = new int[lcIndex.size()];
        Map<LabeledClique, Integer> map = slowMap.get(i);
        for (int j = 0; j < lcIndex.size(); j++) {
          LabeledClique lc = lcIndex.get(j);
          Integer k = map.get(lc);
          if (k == null) {
            FeatureFactoryWrapper.ImmutablePairOfImmutables<String, LabeledClique> p = (FeatureFactoryWrapper.ImmutablePairOfImmutables<String, LabeledClique>)metaInfo.getFeature(i);
            LabeledClique newLC = LabeledClique.valueOf(p.second().clique, lc, 0);
            p = new FeatureFactoryWrapper.ImmutablePairOfImmutables<String, LabeledClique>(p.first(), newLC);
            k = metaInfo.indexOfFeature(p);
            fastMap[i][j] = k;
          } else {
            fastMap[i][j] = k;
          }
          fastMap[k] = fastMap[i];
        }
      }
    }
    slowMap = null;
  }
  
}
