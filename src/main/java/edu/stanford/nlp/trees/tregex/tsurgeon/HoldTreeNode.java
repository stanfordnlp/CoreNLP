package edu.stanford.nlp.trees.tregex.tsurgeon;

import java.util.Map;

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
  public TsurgeonMatcher matcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
    return new Matcher(newNodeNames, coindexer);
  }

  private class Matcher extends TsurgeonMatcher {
    public Matcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
      super(HoldTreeNode.this, newNodeNames, coindexer);
    }

    @Override
    public Tree evaluate(Tree tree, TregexMatcher tregex) {
      return subTree.copy(this, tree.treeFactory(), tree.label().labelFactory()).tree;
    }
  }

  @Override
  public String toString() {
    return subTree.toString();
  }
}
