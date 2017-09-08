package old.edu.stanford.nlp.trees;

import old.edu.stanford.nlp.ling.Label;
import old.edu.stanford.nlp.ling.LabelFactory;

import java.util.List;


/**
 * A <code>SimpleTreeFactory</code> acts as a factory for creating objects
 * of class <code>SimpleTree</code>.
 * <p/>
 * <i>NB: A SimpleTree stores tree geometries but no node labels.  Make sure
 * this is what you really want.</i>
 *
 * @author Christopher Manning
 */
public class SimpleTreeFactory implements TreeFactory {

  /**
   * Creates a new <code>TreeFactory</code>.  A
   * <code>SimpleTree</code> stores no <code>Label</code>, so no
   * <code>LabelFactory</code> is built.
   */
  public SimpleTreeFactory() {
  }

  /**
   * Creates a new <code>TreeFactory</code>.  A
   * <code>SimpleTree</code> stores no <code>Label</code>, so the
   * <code>LabelFactory</code> argument is ignored.
   *
   * @param lf This argument is ignored
   */
  public SimpleTreeFactory(final LabelFactory lf) {
  }

  public Tree newLeaf(final String word) {
    return new SimpleTree();
  }

  public Tree newLeaf(final Label word) {
    return new SimpleTree();
  }

  public Tree newTreeNode(final String parent, final List<Tree> children) {
    return new SimpleTree(null, children);
  }

  public Tree newTreeNode(final Label parentLabel, final List<Tree> children) {
    return new SimpleTree(parentLabel, children);
  }

}
