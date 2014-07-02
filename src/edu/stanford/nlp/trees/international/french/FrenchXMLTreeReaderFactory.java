package edu.stanford.nlp.trees.international.french;

import java.io.Reader;
import java.io.Serializable;

import edu.stanford.nlp.trees.*;

/**
 * A class for reading French Treebank trees that have been converted
 * from XML to PTB format.
 * 
 * @author Spence Green
 *
 */
public class FrenchXMLTreeReaderFactory implements TreeReaderFactory, Serializable {

  private static final long serialVersionUID = 578942679136874L;

  private final boolean ccTagset;

  public FrenchXMLTreeReaderFactory(boolean ccTagset) {
    this.ccTagset = ccTagset;
  }

  public TreeReader newTreeReader(Reader in) {
    return new FrenchXMLTreeReader(in, ccTagset);
  }
}
