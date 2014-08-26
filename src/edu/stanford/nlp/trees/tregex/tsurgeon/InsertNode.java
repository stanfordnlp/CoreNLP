package edu.stanford.nlp.trees.tregex.tsurgeon;

import java.util.Map;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.util.Pair;

/**
 * @author Roger Levy (rog@stanford.edu)
 */
class InsertNode extends TsurgeonPattern {

  TreeLocation location;

  /**
   * Does the item being inserted need to be deep-copied before
   * insertion?
   */
  boolean needsCopy = true;

  public InsertNode(TsurgeonPattern child, TreeLocation l) {
    super("insert", new TsurgeonPattern[] { child });
    this.location = l;
  }

  @Override
  protected void setRoot(TsurgeonPatternRoot root) {
    super.setRoot(root);
    location.setRoot(root);
  }

  public InsertNode(AuxiliaryTree t, TreeLocation l) {
    this(new HoldTreeNode(t), l);

    // Copy occurs in HoldTreeNode's `evaluate` method
    needsCopy = false;
  }

  @Override
  public TsurgeonMatcher matcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
    return new Matcher(newNodeNames, coindexer);
  }

  private class Matcher extends TsurgeonMatcher {
    TreeLocation.LocationMatcher locationMatcher;

    public Matcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
      super(InsertNode.this, newNodeNames, coindexer);
      locationMatcher = location.matcher(newNodeNames, coindexer);
    }

    @Override
    public Tree evaluate(Tree tree, TregexMatcher tregex) {
      Tree nodeToInsert = childMatcher[0].evaluate(tree, tregex);
      Pair<Tree,Integer> position = locationMatcher.evaluate(tree, tregex);
      position.first().insertDtr(needsCopy ? nodeToInsert.deepCopy() : nodeToInsert, 
                                 position.second());
      return tree;
    }
  }

  @Override
  public String toString() {
    return label + '(' + children[0] + ',' + location + ')';
  }


}
