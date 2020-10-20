package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.Label;

import java.util.List;


/**
 * A {@code SimpleTreeFactory} acts as a factory for creating objects
 * of class {@code SimpleTree}.
 * <br>
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

  @Override
  public Tree newLeaf(final String word) {
    return new SimpleTree();
  }

  @Override
  public Tree newLeaf(final Label word) {
    return new SimpleTree();
  }

  @Override
  public Tree newTreeNode(final String parent, final List<Tree> children) {
    return new SimpleTree(null, children);
  }

  @Override
  public Tree newTreeNode(final Label parentLabel, final List<Tree> children) {
    return new SimpleTree(parentLabel, children);
  }

}
