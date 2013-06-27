package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.StringLabelFactory;

import java.io.Reader;

/**
 * This class implements a <code>TreeReaderFactory</code> that produces
 * labeled, scored array-based Trees, which have been cleaned up to
 * delete empties, etc.  This seems to be a common case.
 *
 * @author Christopher Manning
 * @version 2000/12/29
 */
public class StringLabeledScoredTreeReaderFactory implements TreeReaderFactory {

  /**
   * An implementation of the <code>TreeReaderFactory</code> interface.
   * It creates a simple <code>TreeReader</code> which literally
   * reproduces trees in the treebank as <code>LabeledScoredTree</code>
   * objects, with <code>StringLabel</code> labels.
   */
  public TreeReader newTreeReader(Reader in) {
    return new PennTreeReader(in, new LabeledScoredTreeFactory(new StringLabelFactory()));
  }

}
