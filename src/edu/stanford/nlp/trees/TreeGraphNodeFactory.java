package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.LabelFactory;
import edu.stanford.nlp.ling.CoreLabel;

import java.util.List;


/**
 * A {@code TreeGraphNodeFactory} acts as a factory for creating
 * tree nodes of type {@link TreeGraphNode}.  Unless
 * another {@link LabelFactory} is supplied, it will use a CoreLabelFactory
 * by default.
 *
 * @author Bill MacCartney
 */
public class TreeGraphNodeFactory implements TreeFactory {

  private final LabelFactory mlf;

  /**
   * Make a {@code TreeFactory} that produces
   * {@code TreeGraphNode}s.  The labels are of class
   * {@code CoreLabel}.
   */
  public TreeGraphNodeFactory() {
    this(CoreLabel.factory());
  }

  /**
   * Make a {@code TreeFactory} that produces
   * {@code TreeGraphNode}s.  The labels depend on the
   * {@code LabelFactory}.
   *
   * @param mlf The LabelFactory to use for node labels
   */
  public TreeGraphNodeFactory(LabelFactory mlf) {
    this.mlf = mlf;
  }

  /** {@inheritDoc} */
  @Override
  public Tree newLeaf(final String word) {
    return newLeaf(mlf.newLabel(word));
  }

  /** {@inheritDoc} */
  @Override
  public Tree newLeaf(Label label) {
    return new TreeGraphNode(label);
  }

  /** {@inheritDoc} */
  @Override
  public Tree newTreeNode(final String parent, final List<Tree> children) {
    return newTreeNode(mlf.newLabel(parent), children);
  }

  /** {@inheritDoc} */
  @Override
  public Tree newTreeNode(Label parentLabel, List<Tree> children) {
    return new TreeGraphNode(parentLabel, children);
  }

}
