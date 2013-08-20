
package edu.stanford.nlp.ie;

import edu.stanford.nlp.util.PaddedList;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.sequences.Clique;

import java.util.*;

/**
 *
 * @author Jenny Finkel
 */

//TODO: repair this so it extends edu.stanford.nlp.sequences.FeatureFactory
public class OCRFeatureFactory  {

  private static final long serialVersionUID = -1234L;

  public void init(SeqClassifierFlags flags) {
    //super.init(flags);
  }


  /**
   * Extracts all the features from the input data at a certain index.
   *
   * @param cInfo The complete data set as a List of WordInfo
   * @param loc  The index at which to extract features.
   */
  public Collection<String> getCliqueFeatures(PaddedList<? extends CoreLabel> cInfo, int loc, Clique clique) {
    Collection<String> features = new HashSet<String>();

//    if (clique == cliqueC) {
//      addAllInterningAndSuffixing(features, featuresC(cInfo, loc), "C");
//    } else if (clique == cliqueCpC) {
//      addAllInterningAndSuffixing(features, featuresCpC(cInfo, loc), "CpC");
//    }

    return features;
  }


  protected Collection<String> featuresC(PaddedList<? extends CoreLabel> cInfo, int loc) {
    cInfo.get(loc);
    Collection<String> featuresC = new ArrayList<String>();
    for (int i = 0; i < 16; i++) {
      for (int j = 0; j < 8; j++) {
        //String f = "p_"+i+"_"+j;
        //featuresC.add(f+"="+c.get(f));
      }
    }
    featuresC.add("##");
    return featuresC;
  }


  protected Collection<String> featuresCpC(PaddedList<? extends CoreLabel> cInfo, int loc) {
    // CoreLabel c = cInfo.get(loc);
    Collection<String> featuresCpC = new ArrayList<String>();
    featuresCpC.add("###");
    return featuresCpC;
  }

}





