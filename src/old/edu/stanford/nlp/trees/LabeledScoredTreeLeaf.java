package old.edu.stanford.nlp.trees;

import old.edu.stanford.nlp.ling.Label;
import old.edu.stanford.nlp.ling.LabelFactory;
import old.edu.stanford.nlp.ling.StringLabel;

/**
 * A <code>LabeledScoredTreeLeaf</code> represents the leaf of a tree
 * in a parse tree with labels and scores.
 *
 * @author Christopher Manning
 * @version 1.0
 */
public class LabeledScoredTreeLeaf extends Tree {

  /**
   *
   */
  private static final long serialVersionUID = 7626389793561604973L;

  /**
   * The string representing the word that is the yield of the parse tree.
   */
  private Label label; // = null;

  /**
   * The score for the leaf
   */
  private double score; // = 0.0

  /**
   * Create an empty leaf parse tree with an empty word.
   */
  public LabeledScoredTreeLeaf() {
  }

  /**
   * Create a leaf parse tree with given word.
   *
   * @param label the <code>Label</code> representing the <i>word</i> for
   *              this new tree leaf.
   */
  public LabeledScoredTreeLeaf(Label label) {
    this.label = label;
  }

  /**
   * Create a leaf parse tree with given word and score.
   *
   * @param label The <code>Label</code> representing the <i>word</i> for
   * @param score The score for the node
   *              this new tree leaf.
   */
  public LabeledScoredTreeLeaf(Label label, double score) {
    this.label = label;
    this.score = score;
  }

  /**
   * Indicates that <code>this</code> is a leaf.
   * CHRIS: is adding this actually a speed-up or a slowdown?  Check!
   *
   * @return Whether this is a leaf node
   */
  @Override
  public boolean isLeaf() {
    return true;
  }

  /**
   * Leaves have no children.
   *
   * @return <code>a unique zero-length Tree[]</code>
   */
  @Override
  public Tree[] children() {
    return EMPTY_TREE_ARRAY;
  }

  /**
   * Leaves have no children.  Returns an <code>UnsupportedOperationException</code>
   *
   * @param children The children for the node
   */
  @Override
  public void setChildren(Tree[] children) {
    throw new UnsupportedOperationException();
  }

  /**
   * Convert tree leaf to its label's string.
   *
   * @return Node's label in String form
   */
  @Override
  public String toString() {
    return label.toString();
  }

  /**
   * Appends the printed form of a parse tree (as a bracketed String)
   * to a <code>StringBuffer</code>.
   *
   * @return StringBuffer returns the <code>StringBuffer</code>
   */
  @Override
  public StringBuilder toStringBuilder(StringBuilder sb) {
    return sb.append(toString());
  }

  /**
   * Returns the label associated with the current node.
   *
   * @return The label associated with the current node, or
   *         <code>null</code> if there is no label
   */
  @Override
  public Label label() {
    return label;
  }

  /**
   * Sets the label associated with the current node, if there is one.
   *
   * @param label The label of the node
   */
  @Override
  public void setLabel(Label label) {
    this.label = label;
  }

  /**
   * Returns the node's score.
   *
   * @return The score associated with the current node, or NaN
   *         if there is no score
   */
  @Override
  public double score() {
    return score;
  }

  /**
   * Sets the score associated with the current node, if there is one.
   *
   * @param score Score of node
   */
  @Override
  public void setScore(double score) {
    this.score = score;
  }

  /**
   * Leaves have no children.  Returns an <code>UnsupportedOperationException</code>
   */
  @Override
  public void insertDtr(Tree dtr, int position) {
    throw new UnsupportedOperationException();
  }

  /**
   * Leaves have no children.  Returns an <code>UnsupportedOperationException</code>
   */
  @Override
  public void addChild(int i, Tree t) {
    throw new UnsupportedOperationException();
  }

  /**
   * Leaves have no children.  Returns an <code>UnsupportedOperationException</code>
   */
  @Override
  public void addChild(Tree t) {
    throw new UnsupportedOperationException();
  }

  /**
   * Leaves have no children.  Returns an <code>UnsupportedOperationException</code>
   */
  @Override
  public Tree setChild(int i, Tree t) {
    throw new UnsupportedOperationException();
  }

  /**
   * Return a <code>TreeFactory</code> that produces trees of the
   * same type as the current <code>Tree</code>.  That is, this
   * implementation, will produce trees of type
   * <code>LabeledScoredTree(Node|Leaf)</code>.
   * The <code>Label</code> of <code>this</code>
   * is examined, and providing it is not <code>null</code>, a
   * <code>LabelFactory</code> which will produce that kind of
   * <code>Label</code> is supplied to the <code>TreeFactory</code>.
   * If the <code>Label</code> is <code>null</code>, a
   * <code>StringLabelFactory</code> will be used.
   * The factories returned on different calls a different: a new one is
   * allocated each time.
   *
   * @return a factory to produce labeled, scored trees
   */
  @Override
  public TreeFactory treeFactory() {
    LabelFactory lf;
    if (label() != null) {
      lf = label().labelFactory();
    } else {
      lf = StringLabel.factory();
    }
    return new LabeledScoredTreeFactory(lf);
  }

  /**
   * Return a <code>TreeFactory</code> that produces trees of the
   * <code>LabeledScoredTree{Node|Leaf}</code> type.
   * The factory returned is always the same one (a singleton).
   *
   * @return a factory to produce labeled, scored trees
   */
  public static TreeFactory factory() {
    return LabeledScoredTreeNode.factory();
  }

  /**
   * Return a <code>TreeFactory</code> that produces trees of the
   * <code>LabeledScoredTree{Node|Leaf}</code> type, with
   * the <code>Label</code> made with the supplied
   * <code>LabelFactory</code>.
   * The factory returned is a different one each time
   *
   * @param lf The LabelFactory to use
   * @return a factory to produce labeled, scored trees
   */
  public static TreeFactory factory(LabelFactory lf) {
    return LabeledScoredTreeNode.factory(lf);
  }

}

