package edu.stanford.nlp.trees.tregex.tsurgeon;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;

/**
 * @author Roger Levy (rog@stanford.edu)
 */
class ReplaceNode extends TsurgeonPattern {

  public ReplaceNode(TsurgeonPattern oldNode, TsurgeonPattern newNode) {
    super("replace", new TsurgeonPattern[] { oldNode, newNode });
  }

  public ReplaceNode(TsurgeonPattern oldNode, AuxiliaryTree t) {
    this(oldNode, new HoldTreeNode(t));
  }

  @Override
  public Tree evaluate(Tree t, TregexMatcher m) {
    Tree oldNode = children[0].evaluate(t,m);
    Tree newNode = children[1].evaluate(t,m);
    if(oldNode==t)
      return newNode;
    Tree parent = oldNode.parent(t);
    int i = parent.objectIndexOf(oldNode);
    parent.removeChild(i);
    parent.insertDtr(newNode.deepCopy(),i);
    return t;
  }
}
