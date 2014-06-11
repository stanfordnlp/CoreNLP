package edu.stanford.nlp.trees.tregex.tsurgeon;

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
  public Tree evaluate(Tree t, TregexMatcher m) {
    // find match
    Tree targetNode = children[0].evaluate(t,m);
    // put children underneath target in foot of auxilary tree
    AuxiliaryTree ft = adjunctionTree().copy(this);
    ft.foot.setChildren(targetNode.getChildrenAsList());
    // put children of auxiliary tree under target.  root of auxiliary tree is ignored.  root of original is maintained.
    targetNode.setChildren(ft.tree.getChildrenAsList());
    return t;
  }

}
