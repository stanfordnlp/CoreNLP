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

  private static final long serialVersionUID = 2019486878175311263L;

  private final boolean simplifiedTagset;
  private final boolean aggressiveNormalization;

  public SpanishXMLTreeReaderFactory(boolean simplifiedTagset,
                                     boolean aggressiveNormalization) {
    this.simplifiedTagset = simplifiedTagset;
    this.aggressiveNormalization = aggressiveNormalization;
  }

  public TreeReader newTreeReader(Reader in) {
    return new SpanishXMLTreeReader(in, simplifiedTagset, aggressiveNormalization);
  }
}
