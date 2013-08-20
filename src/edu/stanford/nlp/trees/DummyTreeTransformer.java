package edu.stanford.nlp.trees;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeTransformer;

/**
 * This is a dummy class for transforming a
 * local <code>Tree</code>.  It just returns the unchanged tree.
 *
 * @author Jeanette Pettibone
 */
public class DummyTreeTransformer implements TreeTransformer {

  //protected TreeFactory tf = new LabeledScoredTreeFactory();

  /**
   * Does whatever one needs to do to a particular tree.
   * This routine is passed a whole <code>Tree</code>, and could itself
   * work recursively, but the canonical usage is to invoke this method
   * via the <code>Tree.transform()</code> method, which will apply the
   * transformer in a bottom-up manner to each local <code>Tree</code>,
   * and hence the implementation of <code>TreeTransformer</code> should
   * merely examine and change a local (one-level) <code>Tree</code>.
   * The DummyTreeTransformer doesn't transform the tree - it merely
   * returns the same tree.
   *
   * @param t  A tree.  Classes implementing this interface can assume
   *           that the tree passed in is not <code>null</code>.
   * @return the transformed <code>Tree</code>
   */
  public Tree transformTree(Tree t) {
    return t;
  }

}
