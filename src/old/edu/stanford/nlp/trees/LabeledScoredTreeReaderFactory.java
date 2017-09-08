package old.edu.stanford.nlp.trees;

import old.edu.stanford.nlp.ling.CategoryWordTagFactory;
import old.edu.stanford.nlp.ling.LabelFactory;

import java.io.Reader;

/**
 * This class implements a <code>TreeReaderFactory</code> that produces
 * labeled, scored array-based Trees, which have been cleaned up to
 * delete empties, etc.   This seems to be a common case (for English).
 * By default, the labels are of type CategoryWordTag,
 * but a different Label type can be specified by the user.
 *
 * @author Christopher Manning
 */
public class LabeledScoredTreeReaderFactory implements TreeReaderFactory {

  private final LabelFactory lf;
  private final TreeNormalizer tm;

  /**
   * Create a new TreeReaderFactory with CategoryWordTag labels.
   */
  public LabeledScoredTreeReaderFactory() {
    lf = new CategoryWordTagFactory();
    tm = new BobChrisTreeNormalizer();
  }

  public LabeledScoredTreeReaderFactory(LabelFactory lf) {
    this.lf = lf;
    tm = new BobChrisTreeNormalizer();
  }

  public LabeledScoredTreeReaderFactory(TreeNormalizer tm) {
    lf = new CategoryWordTagFactory();
    this.tm = tm;
  }

  /**
   * An implementation of the <code>TreeReaderFactory</code> interface.
   * It creates a <code>TreeReader</code> which normalizes trees using
   * the <code>BobChrisTreeNormalizer</code>, and makes
   * <code>LabeledScoredTree</code> objects with
   * <code>CategoryWordTag</code> labels (unless otherwise specified on
   * construction).
   */
  public TreeReader newTreeReader(Reader in) {
    return new PennTreeReader(in, new LabeledScoredTreeFactory(lf), tm);
  }
}
