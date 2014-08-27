package edu.stanford.nlp.trees.tregex.tsurgeon;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;

/**
 * @author Roger Levy (rog@stanford.edu)
 */
class HoldTreeNode extends TsurgeonPattern {

  AuxiliaryTree subTree;

  public HoldTreeNode(AuxiliaryTree t) {
    super("hold", TsurgeonPattern.EMPTY_TSURGEON_PATTERN_ARRAY);
    this.subTree = t;
  }

  @Override
  public Tree evaluate(Tree t, TregexMatcher m) {
    return subTree.copy(this).tree;
  }

  @Override
  public String toString() {
    return subTree.toString();
  }
}
