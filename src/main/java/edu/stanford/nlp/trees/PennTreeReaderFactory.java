package edu.stanford.nlp.trees;

import java.io.Reader;

/** Vends {@link PennTreeReader} objects.
 *
 *  @author Roger Levy (rog@nlp.stanford.edu)
 */
public class PennTreeReaderFactory implements TreeReaderFactory {

  private final TreeFactory tf;
  private final TreeNormalizer tn;

  /**
   * Default constructor; uses a {@link LabeledScoredTreeFactory},
   * with StringLabels, a {@link PennTreebankTokenizer},
   * and a {@link TreeNormalizer}.
   */
  public PennTreeReaderFactory() {
    this(new LabeledScoredTreeFactory());
  }

  /**
   * Specify your own {@link TreeFactory};
   * uses a {@link PennTreebankTokenizer}, and a {@link TreeNormalizer}.
   *
   * @param tf The TreeFactory to use in building Tree objects to return.
   */
  public PennTreeReaderFactory(TreeFactory tf) {
    this(tf, new TreeNormalizer());
  }


  /**
   * Specify your own {@link TreeNormalizer};
   * uses a {@link PennTreebankTokenizer}, and a {@link LabeledScoredTreeFactory}.
   *
   * @param tn The TreeNormalizer to use in building Tree objects to return.
   */
  public PennTreeReaderFactory(TreeNormalizer tn) {
    this(new LabeledScoredTreeFactory(), tn);
  }


  /**
   * Specify your own {@link TreeFactory};
   * uses a {@link PennTreebankTokenizer}, and a {@link TreeNormalizer}.
   *
   * @param tf The TreeFactory to use in building Tree objects to return.
   * @param tn The TreeNormalizer to use
   */
  public PennTreeReaderFactory(TreeFactory tf, TreeNormalizer tn) {
    this.tf = tf;
    this.tn = tn;
  }


  @Override
  public TreeReader newTreeReader(Reader in) {
    return new PennTreeReader(in, tf, tn, new PennTreebankTokenizer(in));
  }

}
