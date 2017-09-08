package old.edu.stanford.nlp.trees;

import old.edu.stanford.nlp.ling.*;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;

/**
 * A <code>LabeledScoredTreeNode</code> represents a tree composed of a root
 * label, a score,
 * and an array of daughter parse trees.  A parse tree derived from a rule
 * provides information about the category of the root as well as a composite
 * of the daughter categories.
 *
 * @author Christopher Manning
 */
public class LabeledScoredTreeNode extends Tree {
  /**
   *
   */
  private static final long serialVersionUID = -8992385140984593817L;
  /**
   * Label of the parse tree.
   */
  private Label label;
  /**
   * Score of <code>TreeNode</code>
   */
  private double score = Double.NaN;
  /**
   * Daughters of the parse tree.
   */
  private Tree[] daughterTrees;

  /**
   * Create an empty parse tree.
   */
  public LabeledScoredTreeNode() {
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
   * Sets the children of this <code>Tree</code>.  If given
   * <code>null</code>, this method prints a warning and sets the
   * Tree's children to the canonical zero-length Tree[] array.
   * Constructing a LabeledScoredTreeLeaf is preferable in this
   * case.
   *
   * @param children An array of child trees
   */
  @Override
  public void setChildren(Tree[] children) {
    if (children == null) {
      System.err.println("Warning -- you tried to set the children of a LabeledScoredTreeNode to null.\nYou really should be using a zero-length array instead.\nConsider building a LabeledScoredTreeLeaf instead.");
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
   * Appends the printed form of a parse tree (as a bracketed String)
   * to a <code>StringBuffer</code>.
   *
   * @return StringBuffer returns the <code>StringBuffer</code> @param sb
   */
  @Override
  public StringBuilder toStringBuilder(StringBuilder sb) {
    sb.append('(');
    sb.append(nodeString());
    for (Tree daughterTree : daughterTrees) {
      sb.append(' ');
      daughterTree.toStringBuilder(sb);
    }
    return sb.append(')');
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

  // extra class guarantees correct lazy loading (Bloch p.194)
  private static class TreeFactoryHolder {
    static final TreeFactory tf = new LabeledScoredTreeFactory();
  }

  /**
   * Return a <code>TreeFactory</code> that produces trees of the
   * <code>LabeledScoredTree{Node|Leaf}</code> type.
   * The factory returned is always the same one (a singleton).
   *
   * @return a factory to produce labeled, scored trees
   */
  public static TreeFactory factory() {
    return TreeFactoryHolder.tf;
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
    return new LabeledScoredTreeFactory(lf);
  }

  private static NumberFormat nf = new DecimalFormat("0.000");

  @Override
  public String nodeString() {
    StringBuilder buff = new StringBuilder();
    buff.append(super.nodeString());
    if ( ! Double.isNaN(score)) {
      buff.append(" [").append(nf.format(-score)).append("]");
    }
    return buff.toString();
  }
}

