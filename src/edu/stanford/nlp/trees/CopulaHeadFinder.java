package edu.stanford.nlp.trees;

/**
 * A mix-in interface for HeadFinders which support the
 * makesCopulaHead method, which says how the HeadFinder in question
 * handles "to be" verbs.
 */
public interface CopulaHeadFinder {
  public boolean makesCopulaHead();
}
