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

  public TreeReader newTreeReader(Reader in) {
    return new FrenchTreeReader(in);
  }
}
