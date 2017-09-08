package old.edu.stanford.nlp.trees;

import java.io.Serializable;

/**
 * An interface for finding the "head" daughter of a phrase structure tree.
 * This could potentially be any sense of "head", but has mainly been used
 * to find the lexical head for lexicalized PCFG parsing.
 *
 * @author Christopher Manning
 */
public interface HeadFinder extends Serializable {

  /**
   * Determine which daughter of the current parse tree is the head.
   * It assumes that the daughters already have had their heads
   * determined. Another method has to do the tree walking.
   *
   * @param t The parse tree to examine the daughters of
   * @return The parse tree that is the head.  The convention has been
   *         that this returns <code>null</code> if no head is found.
   *         But maybe it should throw an exception?
   */
  public Tree determineHead(Tree t);


  /**
   * Determine which daughter of the current parse tree is the head given the parent of
   * the tree.
   * It assumes that the daughters already have had their heads
   * determined. Another method has to do the tree walking.
   *
   * @param t The parse tree to examine the daughters of
   * @param parent The parent of tree t
   * @return The parse tree that is the head.  The convention has been
   *         that this returns <code>null</code> if no head is found.
   *         But maybe it should throw an exception?
   */
  public Tree determineHead(Tree t, Tree parent);




}
