package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.Label;

import java.util.List;

/**
 * A <code>SimpleTree</code> is a minimal concrete implementation of an
 * unlabeled, unscored <code>Tree</code>.  It has a tree structure, but no
 * annotation at nodes.
 *
 * @author Christopher Manning
 */
public class SimpleTree extends Tree {

  /**
   * 
   */
  private static final long serialVersionUID = -8075763706877132926L;
  /**
   * Daughters of the parse tree.
   */
  private Tree[] daughterTrees;

  /**
   * Create an empty parse tree.
   */
  public SimpleTree() {
    daughterTrees = EMPTY_TREE_ARRAY;
  }

  /**
   * Create parse tree with given root and null daughters.
   *
   * @param label root label of new tree to construct.  For a SimpleTree
   *              this parameter is ignored.
   */
  public SimpleTree(Label label) {
    this();
  }

  /**
   * Create parse tree with given root and array of daughter trees.
   *
   * @param label             root label of tree to construct.  For a SimpleTree
   *                          this parameter is ignored
   * @param daughterTreesList list of daughter trees to construct.
   */
  public SimpleTree(Label label, List<Tree> daughterTreesList) {
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
   * <code>null</code>, this method sets the Tree's children to a
   * unique zero-length Tree[] array.
   *
   * @param children An array of child trees
   */
  @Override
  public void setChildren(Tree[] children) {
    if (children == null) {
      System.err.println("Warning -- you tried to set the children of a SimpleTree to null.\nYou should be really using a zero-length array instead.");
      daughterTrees = EMPTY_TREE_ARRAY;
    } else {
      daughterTrees = children;
    }
  }


  // extra class guarantees correct lazy loading (Bloch p.194)
  private static class TreeFactoryHolder {
    static final TreeFactory tf = new SimpleTreeFactory();
  }


  /**
   * Return a <code>TreeFactory</code> that produces trees of the
   * <code>SimpleTree</code> type.
   * The factory returned is always the same one (a singleton).
   *
   * @return a factory to produce simple (unlabelled) trees
   */
  @Override
  public TreeFactory treeFactory() {
    return TreeFactoryHolder.tf;
  }


  /**
   * Return a <code>TreeFactory</code> that produces trees of the
   * <code>SimpleTree</code> type.
   * The factory returned is always the same one (a singleton).
   *
   * @return a factory to produce simple (unlabelled) trees
   */
  public static TreeFactory factory() {
    return TreeFactoryHolder.tf;
  }

}
