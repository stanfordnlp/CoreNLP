package edu.stanford.nlp.trees.tregex.tsurgeon;

import java.util.Map;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;

/** Adjoin in a tree (like in TAG), but retain the target of adjunction as the root of the auxiliary tree.
 * @author Roger Levy (rog@nlp.stanford.edu)
 */
class AdjoinToHeadNode extends AdjoinNode {

  public AdjoinToHeadNode(AuxiliaryTree t, TsurgeonPattern p) {
    super("adjoinH", t, p);
  }

  @Override
  public TsurgeonMatcher matcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
    return new Matcher(newNodeNames, coindexer);
  }

  private class Matcher extends TsurgeonMatcher {
    public Matcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
      super(AdjoinToHeadNode.this, newNodeNames, coindexer);
    }

    @Override
    public Tree evaluate(Tree tree, TregexMatcher tregex) {
      // find match
      Tree targetNode = childMatcher[0].evaluate(tree, tregex);
      // put children underneath target in foot of auxilary tree
      AuxiliaryTree ft = adjunctionTree().copy(this, tree.treeFactory(), tree.label().labelFactory());
      ft.foot.setChildren(targetNode.getChildrenAsList());
      // put children of auxiliary tree under target.  root of auxiliary tree is ignored.  root of original is maintained.
      targetNode.setChildren(ft.tree.getChildrenAsList());
      return tree;
    }
  }

}
