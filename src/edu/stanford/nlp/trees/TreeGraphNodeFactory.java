package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.LabelFactory;
import edu.stanford.nlp.ling.CyclicCoreLabel;

import java.util.List;


/**
 * A <code>TreeGraphNodeFactory</code> acts as a factory for creating
 * nodes in a {@link TreeGraph <code>TreeGraph</code>}.  Unless
 * another {@link LabelFactory <code>LabelFactory</code>} is
 * supplied, it will use a CyclicCoreLabelFactory
 * by default.
 *
 * @author Bill MacCartney
 */
public class TreeGraphNodeFactory implements TreeFactory {

  private LabelFactory mlf;

  /**
   * Make a <code>TreeFactory</code> that produces
   * <code>TreeGraphNode</code>s.  The labels are of class
   * <code>CyclicCoreLabel</code>.
   */
  public TreeGraphNodeFactory() {
    this(CyclicCoreLabel.factory());
  }

  public TreeGraphNodeFactory(LabelFactory mlf) {
    this.mlf = mlf;
  }

  // docs inherited
  public Tree newLeaf(final String word) {
    return newLeaf(mlf.newLabel(word));
  }

  // docs inherited
  public Tree newLeaf(Label label) {
    return new TreeGraphNode(label);
  }

  // docs inherited
  public Tree newTreeNode(final String parent, final List<Tree> children) {
    return newTreeNode(mlf.newLabel(parent), children);
  }

  // docs inherited
  public Tree newTreeNode(Label parentLabel, List<Tree> children) {
    return new TreeGraphNode(parentLabel, children);
  }

}

