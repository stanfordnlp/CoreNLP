package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.LabelFactory;

import java.io.Reader;

/**
 * This class implements a <code>TreeReaderFactory</code> that produces
 * labeled, scored array-based Trees, which have been cleaned up to
 * delete empties, etc.   This seems to be a common case (for English).
 * <br>
 * By default, the labels are of type CoreLabel,
 * but a different Label type can be specified by the user.
 *
 * @author Christopher Manning
 */
public class LabeledScoredTreeReaderFactory implements TreeReaderFactory {

  private final LabelFactory lf;
  private final TreeNormalizer tm;

  /**
   * Create a new TreeReaderFactory with CoreLabel labels.
   */
  public LabeledScoredTreeReaderFactory() {
    lf = CoreLabel.factory();
    tm = new BobChrisTreeNormalizer();
  }

  public LabeledScoredTreeReaderFactory(LabelFactory lf) {
    this.lf = lf;
    tm = new BobChrisTreeNormalizer();
  }

  public LabeledScoredTreeReaderFactory(TreeNormalizer tm) {
    lf = CoreLabel.factory();
    this.tm = tm;
  }

  public LabeledScoredTreeReaderFactory(LabelFactory lf, TreeNormalizer tm) {
    this.lf = lf;
    this.tm = tm;
  }

  /**
   * An implementation of the <code>TreeReaderFactory</code> interface.
   * It creates a <code>TreeReader</code> which normalizes trees using
   * the <code>BobChrisTreeNormalizer</code>, and makes
   * <code>LabeledScoredTree</code> objects with
   * <code>CoreLabel</code> labels (unless otherwise specified on
   * construction).
   */
  public TreeReader newTreeReader(Reader in) {
    return new PennTreeReader(in, new LabeledScoredTreeFactory(lf), tm);
  }
}
