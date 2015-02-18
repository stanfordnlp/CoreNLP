package edu.stanford.nlp.trees.tregex.tsurgeon;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.util.Pair;

/**
 * @author Roger Levy (rog@stanford.edu)
 */
class InsertNode extends TsurgeonPattern {

  TreeLocation l;

  public InsertNode(TsurgeonPattern child, TreeLocation l) {
    super("insert", new TsurgeonPattern[] { child });
    this.l = l;
  }

  @Override
  protected void setRoot(TsurgeonPatternRoot root) {
    super.setRoot(root);
    l.setRoot(root);
  }

  public InsertNode(AuxiliaryTree t, TreeLocation l) {
    this(new HoldTreeNode(t),l);
  }

  @Override
  public Tree evaluate(Tree t, TregexMatcher m) {
    Tree nodeToInsert = children[0].evaluate(t,m);
    Pair<Tree,Integer> position = l.evaluate(t,m);
    position.first().insertDtr(nodeToInsert.deepCopy(),position.second());
    return t;
  }

  @Override
  public String toString() {
    return label + '(' + children[0] + ',' + l + ')';
  }


}
