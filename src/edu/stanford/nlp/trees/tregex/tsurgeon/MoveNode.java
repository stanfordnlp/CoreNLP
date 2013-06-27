package edu.stanford.nlp.trees.tregex.tsurgeon;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Trees;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.util.Pair;

/** Does a delete (NOT prune!) + insert operation
 * @author Roger Levy (rog@stanford.edu)
 */
class MoveNode extends TsurgeonPattern {
  TreeLocation l;

  public MoveNode(TsurgeonPattern child, TreeLocation l) {
    super("move", new TsurgeonPattern[] { child });
    this.l = l;
  }

  @Override
  protected void setRoot(TsurgeonPatternRoot root) {
    super.setRoot(root);
    l.setRoot(root);
  }

  @Override
  public Tree evaluate(Tree t, TregexMatcher m) {
    Tree nodeToMove = children[0].evaluate(t,m);
    Tree oldParent = nodeToMove.parent(t);
    oldParent.removeChild(Trees.objectEqualityIndexOf(oldParent,nodeToMove));
    Pair<Tree,Integer> position = l.evaluate(t,m);
    position.first().insertDtr(nodeToMove,position.second());
    return t;
  }

  @Override
  public String toString() {
    return label + "(" + children[0] + " " + l + ")"; 
  }


}
