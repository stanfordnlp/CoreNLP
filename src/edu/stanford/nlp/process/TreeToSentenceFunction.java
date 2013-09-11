package edu.stanford.nlp.process;


import edu.stanford.nlp.util.Function;


import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.trees.Tree;

import java.util.ArrayList;

/**
 * Function that turns a Tree into its Sentence yield.
 * This is essentially flattening out the tree structure.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public class TreeToSentenceFunction implements Function<Tree,ArrayList<Label>> {
  /**
   * Returns the Sentence yield of the given Tree.
   */
  public ArrayList<Label> apply(Tree tree) {
    return ((tree).yield());
  }
}

