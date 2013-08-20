package edu.stanford.nlp.trees.international.icegb;

import edu.stanford.nlp.ling.StringLabelFactory;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.TreeReader;

import java.io.Reader;

/**
 * The <code>ICEGBTreeReaderFactory</code> is a factory for creating objects of
 * class <code>ICEGBTreeReader</code>.
 *
 * @author Jeanette Pettibone
 */
public class ICEGBTreeReaderFactory {

  public ICEGBTreeReaderFactory() {
  }

  public TreeReader newTreeReader(Reader in) {
    return new ICEGBTreeReader(in, new LabeledScoredTreeFactory(new StringLabelFactory()));
  }

}
