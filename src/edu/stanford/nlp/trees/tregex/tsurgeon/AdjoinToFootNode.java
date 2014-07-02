package edu.stanford.nlp.trees.tregex.tsurgeon;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;

/** Adjoin in a tree (like in TAG), but retain the target of adjunction as the foot of the auxiliary tree.
 * @author Roger Levy (rog@nlp.stanford.edu)
 */
class AdjoinToFootNode extends AdjoinNode {

  public AdjoinToFootNode(AuxiliaryTree t, TsurgeonPattern p) {
    super("adjoinF", t, p);
  }

  @Override
  public Tree evaluate(Tree t, TregexMatcher m) {
    // find match and get its parent
    Tree targetNode = children[0].evaluate(t,m);
    Tree parent = targetNode.parent(t);
    // substitute original node for foot of auxiliary tree.  Foot node is ignored
    AuxiliaryTree ft = adjunctionTree().copy(this);
    // System.err.println("ft=" + ft + "; ft.foot=" + ft.foot + "; ft.tree=" + ft.tree);
    Tree parentOfFoot = ft.foot.parent(ft.tree);
    if (parentOfFoot == null) {
      System.err.println("Warning: adjoin to foot for depth-1 auxiliary tree has no effect.");
      return t;
    }
    int i = parentOfFoot.objectIndexOf(ft.foot);
    if (parent==null) {
      parentOfFoot.setChild(i,targetNode);
      return ft.tree;
    } else {
      int j = parent.objectIndexOf(targetNode);
      parent.setChild(j,ft.tree);
      parentOfFoot.setChild(i,targetNode);
      return t;
    }
  }

}
