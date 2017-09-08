package old.edu.stanford.nlp.trees.tregex.tsurgeon;

import old.edu.stanford.nlp.trees.Tree;
import old.edu.stanford.nlp.trees.tregex.TregexMatcher;

/** Adjoin in a tree (like in TAG).
 *
 *  @author Roger Levy (rog@nlp.stanford.edu)
 */
class AdjoinNode extends TsurgeonPattern {

  private final AuxiliaryTree adjunctionTree;

  public AdjoinNode(AuxiliaryTree t, TsurgeonPattern p) {
    super("adjoin", new TsurgeonPattern[] {p});
    if (t == null || p == null) {
      throw new IllegalArgumentException("AdjoinNode: illegal null argument, t=" + t + ", p=" + p);
    }
    adjunctionTree = t;
  }

  @Override
  public Tree evaluate(Tree t, TregexMatcher m) {
    Tree targetNode = children[0].evaluate(t,m);
    Tree parent = targetNode.parent(t);
    AuxiliaryTree ft = adjunctionTree.copy(this);
    ft.foot.setChildren(targetNode.getChildrenAsList());
    if (parent==null) {
      return ft.tree;
    } else {
      int i = parent.indexOf(targetNode);
      parent.setChild(i,ft.tree);
      return t;
    }
  }

  @Override
  public String toString() {
    return super.toString() + "<-" + adjunctionTree.toString();
  }




}
