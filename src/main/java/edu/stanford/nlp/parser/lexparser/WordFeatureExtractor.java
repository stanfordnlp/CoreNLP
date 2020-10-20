package edu.stanford.nlp.parser.lexparser;

import java.io.Serializable;
import java.util.Collection;

/** An interface for getting features out of words for a feature-based lexicon.
 *
 *  @author Galen Andrew
 */
public interface WordFeatureExtractor extends Serializable {

  public void setFeatureLevel(int level);

  public Collection<String> makeFeatures(String word);

  public void applyFeatureCountThreshold(Collection<String> data, int thresh);

}
