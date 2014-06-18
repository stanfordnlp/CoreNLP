package edu.stanford.nlp.trees.international.spanish;

import java.io.Reader;
import java.io.Serializable;

import edu.stanford.nlp.trees.*;

/**
 * A class for reading Spanish Treebank trees that have been converted
 * from XML to PTB format.
 *
 * @author Spence Green
 */
public class SpanishXMLTreeReaderFactory implements TreeReaderFactory, Serializable {

  private static final long serialVersionUID = 578942679136874L;

  private final boolean ccTagset;

  public SpanishXMLTreeReaderFactory(boolean ccTagset) {
    this.ccTagset = ccTagset;
  }

  public TreeReader newTreeReader(Reader in) {
    return new SpanishXMLTreeReader(in, ccTagset);
  }
}
