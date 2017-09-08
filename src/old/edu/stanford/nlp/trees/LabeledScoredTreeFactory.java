package old.edu.stanford.nlp.trees;

import old.edu.stanford.nlp.ling.Label;
import old.edu.stanford.nlp.ling.LabelFactory;
import old.edu.stanford.nlp.ling.StringLabel;

import java.util.List;

/**
 * A <code>LabeledScoredTreeFactory</code> acts as a factory for creating
 * trees with labels and scores.  Unless another <code>LabelFactory</code>
 * is supplied, it will use a <code>StringLabel</code> by default.
 *
 * @author Christopher Manning
 */
public class LabeledScoredTreeFactory extends SimpleTreeFactory {

  private LabelFactory lf;

  /**
   * Make a TreeFactory that produces LabeledScoredTree trees.
   * The labels are of class <code>StringLabel</code>.
   */
  public LabeledScoredTreeFactory() {
    this(StringLabel.factory());
  }

  /**
   * Make a treefactory that uses LabeledScoredTree trees, where the
   * labels are as specified by the user.
   *
   * @param lf the <code>LabelFactory</code> to be used to create labels
   */
  public LabeledScoredTreeFactory(LabelFactory lf) {
    this.lf = lf;
  }

  @Override
  public Tree newLeaf(final String word) {
    return newLeaf(lf.newLabel(word));
  }

  /**
   * Create a new leaf node with the given label
   *
   * @param label the label for the leaf node
   * @return A new tree leaf
   */
  @Override
  public Tree newLeaf(Label label) {
    // System.out.println("Calling newLeaf with " + label);
    return new LabeledScoredTreeLeaf(label);
  }

  @Override
  public Tree newTreeNode(final String parent, final List children) {
    return newTreeNode(lf.newLabel(parent), children);
  }

  /**
   * Create a new non-leaf tree node with the given label
   *
   * @param parentLabel The label for the node
   * @param children    A <code>List</code> of the children of this node,
   *                    each of which should itself be a <code>LabeledScoredTree</code>
   * @return A new internal tree node
   */
  @Override
  public Tree newTreeNode(Label parentLabel, List children) {
    // System.out.println("Calling newTreeNode with " + parentLabel);
    return new LabeledScoredTreeNode(parentLabel, children);
  }

}

