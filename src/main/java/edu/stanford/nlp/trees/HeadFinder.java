package edu.stanford.nlp.trees;

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
   *
   * @param t The parse tree to examine the daughters of
   * @return The daughter tree that is the head.  This will always be
   *     non-null. An Exception will be thrown if no head can be determined.
   * @throws IllegalStateException If a subclass has missing or badly
   *     formatted head rule data
   * @throws IllegalArgumentException If the argument Tree has unexpected
   *     phrasal categories in it (and the implementation doesn't just use
   *     some heuristic to always determine some head).
   */
  public Tree determineHead(Tree t);


  /**
   * Determine which daughter of the current parse tree is the head
   * given the parent of the tree.
   *
   * @param t The parse tree to examine the daughters of
   * @param parent The parent of tree t
   * @return The daughter tree that is the head.  This will always be
   *     non-null. An Exception will be thrown if no head can be determined.
   */
  public Tree determineHead(Tree t, Tree parent);

}
