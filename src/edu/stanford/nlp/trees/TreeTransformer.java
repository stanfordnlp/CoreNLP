package edu.stanford.nlp.trees;

/**
 * This is a simple interface for a function that alters a
 * local <code>Tree</code>.
 *
 * @author Christopher Manning.
 */
public interface TreeTransformer {

  /**
   * Does whatever one needs to do to a particular tree.
   * This routine is passed a whole <code>Tree</code>, and could itself
   * work recursively, but the canonical usage is to invoke this method
   * via the <code>Tree.transform()</code> method, which will apply the
   * transformer in a bottom-up manner to each local <code>Tree</code>,
   * and hence the implementation of <code>TreeTransformer</code> should
   * merely examine and change a local (one-level) <code>Tree</code>.
   *
   * @param t  A tree.  Classes implementing this interface can assume
   *           that the tree passed in is not <code>null</code>.
   * @return the transformed <code>Tree</code>
   */
  public Tree transformTree(Tree t); 

}
