package edu.stanford.nlp.trees;

/**
 * This is a simple strategy-type interface for operations that are applied to
 * {@code Tree}. It typically is called iteratively over
 * trees in a {@code Treebank}.  The convention is for {@code TreeVisitor} implementing
 * classes not to affect {@code Tree} instances they operate on, but to accomplish things via
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
   *          that the tree passed in is not {@code null}.
   */
  void visitTree(Tree t);

}

