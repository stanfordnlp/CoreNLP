package edu.stanford.nlp.trees;

/**
 * A general factory for {@link GrammaticalStructure} objects.
 *
 * @author Galen Andrew
 * @author John Bauer
 */
public interface GrammaticalStructureFactory {

  /**
   * Vend a new {@link GrammaticalStructure} based on the given {@link Tree}.
   *
   * @param t the tree to analyze
   * @return a GrammaticalStructure based on the tree
   */
  GrammaticalStructure newGrammaticalStructure(Tree t);
}
