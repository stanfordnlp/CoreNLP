package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.WordFactory;

import java.io.Reader;

/**
 * This class implements a <code>TreeReaderFactory</code> that produces
 * <code>Word</code> labeled, scored array-based Trees, which have been
 * cleaned up to delete empties, etc., according to the
 * <code>BobChrisTreeNormalizer</code>.   This is the type of trees that
 * are used in the <code>edu.stanford.nlp.parser</code> package.
 *
 * @author Christopher Manning
 * @version 2002/05/31
 */
public class WordLabeledScoredTreeReaderFactory implements TreeReaderFactory {

  /**
   * An implementation of the <code>TreeReaderFactory</code> interface.
   * It creates a <code>TreeReader</code> which normalizes trees using
   * the <code>BobChrisTreeNormalizer</code>, and makes
   * <code>LabeledScoredTree</code> objects with
   * <code>Word</code> labels.
   */
  public TreeReader newTreeReader(Reader in) {
    return new PennTreeReader(in, new LabeledScoredTreeFactory(new WordFactory()), new BobChrisTreeNormalizer());
  }

}
