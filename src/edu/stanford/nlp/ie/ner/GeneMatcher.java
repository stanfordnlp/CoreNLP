package edu.stanford.nlp.ie.ner;

import java.util.Properties;
import java.util.Set;

/**
 * Interface for classes that can match genes based on a synonym map.
 *
 * @author Huy Nguyen (<a href="mailto:htnguyen@cs.stanford.edu">htnguyen@cs.stanford.edu</a>)
 */
public interface GeneMatcher {

  /**
   * Sets the synonym map used by this GeneMatcher.
   */
  public void setSynonymMap(BioCreativeSynonymMap map);

  /**
   * Sets the properties used by this GeneMatcher. Use this for runtime configuration of the GeneMatcher.
   */
  public void setProperties(Properties properties);

  /**
   * Gets the matching IDs for the given gene. Returns an empty set if no matches are found.
   */
  public Set<String> getIDs(String gene);
}
