package edu.stanford.nlp.trees.international.pennchinese;

import java.io.Reader;

import edu.stanford.nlp.trees.*;


/**
 * The {@code CTBTreeReaderFactory} is a factory for creating a
 * TreeReader suitable for the Penn Chinese Treebank (CTB).
 * It knows how to ignore the SGML tags in those files.
 * The default reader doesn't delete empty nodes, but an
 * additional static class is provided whose default constructor
 * does give a TreeReader that deletes empty nodes in trees.
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
   * Create a new {@code TreeReader} using the provided
   * {@code Reader}.
   *
   * @param in The {@code Reader} to build on
   * @return The new TreeReader
   */
  @Override
  public TreeReader newTreeReader(Reader in) {
    if (discardFrags) {
      return new FragDiscardingPennTreeReader(in, new LabeledScoredTreeFactory(), tn, new CHTBTokenizer(in));
    } else {
      return new PennTreeReader(in, new LabeledScoredTreeFactory(), tn, new CHTBTokenizer(in));
    }
  }


  public static class NoEmptiesCTBTreeReaderFactory extends CTBTreeReaderFactory {

    public NoEmptiesCTBTreeReaderFactory() {
      super(new BobChrisTreeNormalizer());
    }

  } // end static class NoEmptiesCTBTreeReaderFactory

}
