package edu.stanford.nlp.trees;

/**
 * This is a simple strategy-type interface for operations that are applied to
 * <code>Tree</code>. It typically is called iteratively over
 * trees in a <code>Treebank</code>.  The convention is for <code>TreeVisitor</code> implementing
 * classes not to affect <code>Tree</code> instances they operate on, but to accomplish things via
 * side effects (like counting statistics over trees, etc.).
 *
 * @author Christopher Manning
 * @author Roger Levy
 */
public interface TreeVisitor {

  /**
   * Does whatever one needs to do to a particular parse tree.
   *
   * @param t A tree.  Classes implementing this interface can assume
   *          that the tree passed in is not <code>null</code>.
   */
  public void visitTree(Tree t);

}

