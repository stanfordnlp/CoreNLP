package edu.stanford.nlp.ie.ner;

import java.util.Properties;

/**
 * Abstract class for GeneMatchers. GeneMatcher implementations should inherit
 * from this class rather than implementing the GeneMatcher interface directly.
 *
 * @author Huy Nguyen (<a href="mailto:htnguyen@cs.stanford.edu">htnguyen@cs.stanford.edu</a>)
 */
public abstract class AbstractGeneMatcher implements GeneMatcher {
  BioCreativeSynonymMap map;
  Properties props;

  public void setSynonymMap(BioCreativeSynonymMap map) {
    this.map = map;
  }

  public void setProperties(Properties props) {
    this.props = props;
  }
}
