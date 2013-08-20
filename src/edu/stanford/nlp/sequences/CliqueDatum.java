package edu.stanford.nlp.sequences;

import java.util.Collection;

/**
 * This interface represents the features and
 * values for each possible clique label
 * for a particular datum. 
 *
 * @author Jenny Finkel
 */

public interface CliqueDatum {

  public Features get(LabeledClique lc) ;
  public void setFeatures(LabeledClique lc, int[] features) ;
  public void setFeatures(LabeledClique lc, int[] features, float[] values) ;
  public Collection<LabeledClique> keySet() ;
  
}
