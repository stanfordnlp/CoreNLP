package edu.stanford.nlp.trees.tregex.tsurgeon;

import java.util.Map;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Trees;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.util.Pair;

/**
 * Does a delete or prune + insert operation
 * @author Roger Levy (rog@stanford.edu)
 */
class MoveNode extends TsurgeonPattern {
  final TreeLocation location;
  final boolean prune;

  public MoveNode(TsurgeonPattern child, TreeLocation l, boolean prune) {
    super("move", new TsurgeonPattern[] { child });
    this.location = l;
    this.prune = prune;
  }

  @Override
  protected void setRoot(TsurgeonPatternRoot root) {
    super.setRoot(root);
    location.setRoot(root);
  }

  @Override
  public TsurgeonMatcher matcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
    return new Matcher(newNodeNames, coindexer);
  }

  private class Matcher extends TsurgeonMatcher {
    TreeLocation.LocationMatcher locationMatcher;

    public Matcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
      super(MoveNode.this, newNodeNames, coindexer);
      locationMatcher = location.matcher(newNodeNames, coindexer);
    }

    @Override
    public Tree evaluate(Tree tree, TregexMatcher tregex) {
      Tree nodeToMove = childMatcher[0].evaluate(tree, tregex);
      Tree oldParent = nodeToMove.parent(tree);
      oldParent.removeChild(Trees.objectEqualityIndexOf(oldParent,nodeToMove));
      Pair<Tree,Integer> position = locationMatcher.evaluate(tree, tregex);
      position.first().insertDtr(nodeToMove,position.second());

      // if this is set to prune, march up the tree until the empty branch is pruned
      while (prune && oldParent.children().length == 0 && oldParent != tree) {
        Tree empty = oldParent;
        oldParent = oldParent.parent(tree);
        oldParent.removeChild(Trees.objectEqualityIndexOf(oldParent, empty));
      }
      return tree;
    }
  }

  @Override
  public String toString() {
    return label + "(" + children[0] + " " + location + ")"; 
  }


}
