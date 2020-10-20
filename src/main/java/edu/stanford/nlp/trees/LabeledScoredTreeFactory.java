package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.LabelFactory;

import java.util.List;

/**
 * A <code>LabeledScoredTreeFactory</code> acts as a factory for creating
 * trees with labels and scores.  Unless another <code>LabelFactory</code>
 * is supplied, it will use a <code>CoreLabel</code> by default.
 *
 * @author Christopher Manning
 */
public class LabeledScoredTreeFactory extends SimpleTreeFactory {

  private LabelFactory lf;

  /**
   * Make a TreeFactory that produces LabeledScoredTree trees.
   * The labels are of class <code>CoreLabel</code>.
   */
  public LabeledScoredTreeFactory() {
    this(CoreLabel.factory());
  }

  /**
   * Make a TreeFactory that uses LabeledScoredTree trees, where the
   * labels are as specified by the user.
   *
   * @param lf the <code>LabelFactory</code> to be used to create labels
   */
  public LabeledScoredTreeFactory(LabelFactory lf) {
    this.lf = lf;
  }

  @Override
  public Tree newLeaf(final String word) {
    return new LabeledScoredTreeNode(lf.newLabel(word));
  }

  /**
   * Create a new leaf node with the given label
   *
   * @param label the label for the leaf node
   * @return A new tree leaf
   */
  @Override
  public Tree newLeaf(Label label) {
    return new LabeledScoredTreeNode(lf.newLabel(label));
  }

  @Override
  public Tree newTreeNode(final String parent, final List<Tree> children) {
    return new LabeledScoredTreeNode(lf.newLabel(parent), children);
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
  public Tree newTreeNode(Label parentLabel, List<Tree> children) {
    return new LabeledScoredTreeNode(lf.newLabel(parentLabel), children);
  }
}

