package edu.stanford.nlp.trees;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.LabelFactory;

/**
 * A {@code LabeledScoredTreeNode} represents a tree composed of a root
 * label, a score,
 * and an array of daughter parse trees.  A parse tree derived from a rule
 * provides information about the category of the root as well as a composite
 * of the daughter categories.
 *
 * @author Christopher Manning
 */
public class LabeledScoredTreeNode extends Tree {

  private static final long serialVersionUID = -8992385140984593817L;

  /**
   * Label of the parse tree.
   */
  @SuppressWarnings("serial")
  private Label label; // = null;

  /**
   * Score of {@code TreeNode}
   */
  private double score = Double.NaN;

  /**
   * Daughters of the parse tree.
   */
  private Tree[] daughterTrees; // = null;

  /**
   * Create an empty parse tree.
   */
  public LabeledScoredTreeNode() {
    setChildren(EMPTY_TREE_ARRAY);
  }

  /**
   * Create a leaf parse tree with given word.
   *
   * @param label the {@code Label} representing the <i>word</i> for
   *              this new tree leaf.
   */
  public LabeledScoredTreeNode(Label label) {
    this(label, Double.NaN);
  }

  /**
   * Create a leaf parse tree with given word and score.
   *
   * @param label The {@code Label} representing the <i>word</i> for
   * @param score The score for the node
   *              this new tree leaf.
   */
  public LabeledScoredTreeNode(Label label, double score) {
    this();
    this.label = label;
    this.score = score;
  }

  /**
   * Create parse tree with given root and array of daughter trees.
   *
   * @param label             root label of tree to construct.
   * @param daughterTreesList List of daughter trees to construct.
   */
  public LabeledScoredTreeNode(Label label, List<Tree> daughterTreesList) {
    this.label = label;
    setChildren(daughterTreesList);
  }

  /**
   * Returns an array of children for the current node, or null
   * if it is a leaf.
   */
  @Override
  public Tree[] children() {
    return daughterTrees;
  }

  /**
   * Sets the children of this {@code Tree}.  If given
   * {@code null}, this method sets the Tree's children to
   * the canonical zero-length Tree[] array.
   *
   * @param children An array of child trees
   */
  @Override
  public void setChildren(Tree[] children) {
    if (children == null) {
      daughterTrees = EMPTY_TREE_ARRAY;
    } else {
      daughterTrees = children;
    }
  }

  /**
   * Returns the label associated with the current node, or null
   * if there is no label
   */
  @Override
  public Label label() {
    return label;
  }

  /**
   * Sets the label associated with the current node, if there is one.
   */
  @Override
  public void setLabel(final Label label) {
    this.label = label;
  }

  /**
   * Returns the score associated with the current node, or Nan
   * if there is no score
   */
  @Override
  public double score() {
    return score;
  }

  /**
   * Sets the score associated with the current node, if there is one
   */
  @Override
  public void setScore(double score) {
    this.score = score;
  }

  /**
   * Return a {@code TreeFactory} that produces trees of the
   * same type as the current {@code Tree}.  That is, this
   * implementation, will produce trees of type
   * {@code LabeledScoredTree(Node|Leaf)}.
   * The {@code Label} of {@code this}
   * is examined, and providing it is not {@code null}, a
   * {@code LabelFactory} which will produce that kind of
   * {@code Label} is supplied to the {@code TreeFactory}.
   * If the {@code Label} is {@code null}, a
   * {@code StringLabelFactory} will be used.
   * The factories returned on different calls a different: a new one is
   * allocated each time.
   *
   * @return a factory to produce labeled, scored trees
   */
  @Override
  public TreeFactory treeFactory() {
    LabelFactory lf = (label() == null) ? CoreLabel.factory() : label().labelFactory();
    return new LabeledScoredTreeFactory(lf);
  }

  // extra class guarantees correct lazy loading (Bloch p.194)
  private static class TreeFactoryHolder {
    static final TreeFactory tf = new LabeledScoredTreeFactory();
  }

  /**
   * Return a {@code TreeFactory} that produces trees of the
   * {@code LabeledScoredTree{Node|Leaf}} type.
   * The factory returned is always the same one (a singleton).
   *
   * @return a factory to produce labeled, scored trees
   */
  public static TreeFactory factory() {
    return TreeFactoryHolder.tf;
  }

  /**
   * Return a {@code TreeFactory} that produces trees of the
   * {@code LabeledScoredTree{Node|Leaf}} type, with
   * the {@code Label} made with the supplied
   * {@code LabelFactory}.
   * The factory returned is a different one each time
   *
   * @param lf The LabelFactory to use
   * @return a factory to produce labeled, scored trees
   */
  public static TreeFactory factory(LabelFactory lf) {
    return new LabeledScoredTreeFactory(lf);
  }

  private static final NumberFormat nf = new DecimalFormat("0.000");

  @Override
  public String nodeString() {
    StringBuilder buff = new StringBuilder();
    buff.append(super.nodeString());
    if ( ! Double.isNaN(score)) {
      buff.append(" [").append(nf.format(-score)).append(']');
    }
    return buff.toString();
  }
}

