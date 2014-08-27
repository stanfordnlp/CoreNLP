package edu.stanford.nlp.trees.international.spanish;

import java.io.Reader;
import java.io.Serializable;

import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.PennTreebankTokenizer;
import edu.stanford.nlp.trees.PennTreeReader;
import edu.stanford.nlp.trees.TreeReader;
import edu.stanford.nlp.trees.TreeReaderFactory;

/**
 * A class for reading Spanish AnCora trees that have been converted
 * from XML to PTB format.
 *
 * @author Jon Gauthier
 * @author Spence Green (original French version)
 */
public class SpanishTreeReaderFactory implements TreeReaderFactory, Serializable {

  // TODO
  private static final long serialVersionUID = 8L;

  public TreeReader newTreeReader(Reader in) {
    return new PennTreeReader(in, new LabeledScoredTreeFactory(),
                              new SpanishTreeNormalizer(false, false, false),
                              new PennTreebankTokenizer(in));
  }

}
