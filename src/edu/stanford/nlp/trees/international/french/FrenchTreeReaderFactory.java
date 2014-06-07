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
public class FrenchTreeReaderFactory implements TreeReaderFactory, Serializable {

  private static final long serialVersionUID = 6928967570430642163L;
 
  private final boolean readPennFormat;
  
  public FrenchTreeReaderFactory() {
    this(false);
  }
  
  public FrenchTreeReaderFactory(boolean pennFormat) {
    readPennFormat = pennFormat;
  }
  
  public TreeReader newTreeReader(Reader in) {
    if(readPennFormat) {
      return new PennTreeReader(in, new LabeledScoredTreeFactory(), new FrenchTreeNormalizer(),new PennTreebankTokenizer(in));
    }
    return new FrenchTreeReader(in);
  }
}
