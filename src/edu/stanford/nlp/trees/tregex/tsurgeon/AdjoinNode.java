package edu.stanford.nlp.trees.tregex.tsurgeon;

import java.util.Map;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;

/** Adjoin in a tree (like in TAG).
 *
 *  @author Roger Levy (rog@nlp.stanford.edu)
 */
class AdjoinNode extends TsurgeonPattern {

  private final AuxiliaryTree adjunctionTree;

  public AdjoinNode(AuxiliaryTree t, TsurgeonPattern p) {
    this("adjoin", t, p);
  }

  public AdjoinNode(String name, AuxiliaryTree t, TsurgeonPattern p) {
    super(name, new TsurgeonPattern[] {p});
    if (t == null || p == null) {
      throw new NullPointerException("AdjoinNode: illegal null argument, t=" + t + ", p=" + p);
    }
    adjunctionTree = t;
  }

  protected AuxiliaryTree adjunctionTree() {
    return adjunctionTree;
  }

  @Override
  public TsurgeonMatcher matcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
    return new Matcher(newNodeNames, coindexer);
  }

  private class Matcher extends TsurgeonMatcher {
    public Matcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
      super(AdjoinNode.this, newNodeNames, coindexer);
    }

    @Override
    public Tree evaluate(Tree tree, TregexMatcher tregex) {
      // find match and get its parent
      Tree targetNode = childMatcher[0].evaluate(tree, tregex);
      Tree parent = targetNode.parent(tree);
      // put children underneath target in foot of auxilary tree
      AuxiliaryTree ft = adjunctionTree.copy(this, tree.treeFactory(), tree.label().labelFactory());
      ft.foot.setChildren(targetNode.getChildrenAsList());
      // replace match with root of auxiliary tree
      if (parent==null) {
        return ft.tree;
      } else {
        int i = parent.objectIndexOf(targetNode);
        parent.setChild(i,ft.tree);
        return tree;
      }
    }
  }

  @Override
  public String toString() {
    return super.toString() + "<-" + adjunctionTree.toString();
  }

}
