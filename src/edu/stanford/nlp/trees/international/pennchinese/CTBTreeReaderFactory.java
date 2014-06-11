package edu.stanford.nlp.trees.international.pennchinese;

import java.io.Reader;

import edu.stanford.nlp.trees.*;


/**
 * The <code>CTBTreeReaderFactory</code> is a factory for creating a
 * TreeReader suitable for the Penn CTB.
 *
 * @author Christopher Manning
 */
public class CTBTreeReaderFactory implements TreeReaderFactory {

  private final TreeNormalizer tn;
  private final boolean discardFrags;

  public CTBTreeReaderFactory() {
    this(new TreeNormalizer());
  }

  public CTBTreeReaderFactory(TreeNormalizer tn) {
    this(tn, false);
  }

  public CTBTreeReaderFactory(TreeNormalizer tn, boolean discardFrags) {
    this.tn = tn;
    this.discardFrags = discardFrags;
  }

  /**
   * Create a new <code>TreeReader</code> using the provided
   * <code>Reader</code>.
   *
   * @param in The <code>Reader</code> to build on
   * @return The new TreeReader
   */
  public TreeReader newTreeReader(Reader in) {
    if (discardFrags) {
      return new FragDiscardingPennTreeReader(in, new LabeledScoredTreeFactory(), tn, new CHTBTokenizer(in));
    } else {
      return new PennTreeReader(in, new LabeledScoredTreeFactory(), tn, new CHTBTokenizer(in));
    }
  }
}
