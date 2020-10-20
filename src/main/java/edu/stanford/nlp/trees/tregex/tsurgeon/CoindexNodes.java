package edu.stanford.nlp.trees.tregex.tsurgeon;

import java.util.Map;

import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.Tree;

/**
 * @author Roger Levy (rog@nlp.stanford.edu)
 */
class CoindexNodes extends TsurgeonPattern {

  private static final String coindexationIntroductionString = "-";

  public CoindexNodes(TsurgeonPattern[] children) {
    super("coindex", children);
  }

  @Override
  protected void setRoot(TsurgeonPatternRoot root) {
    super.setRoot(root);
    root.setCoindexes();
  }

  @Override
  public TsurgeonMatcher matcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
    return new Matcher(newNodeNames, coindexer);
  }

  private class Matcher extends TsurgeonMatcher {
    public Matcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
      super(CoindexNodes.this, newNodeNames, coindexer);
    }

    @Override
    public Tree evaluate(Tree tree, TregexMatcher tregex) {
      int newIndex = coindexer.generateIndex();
      for(TsurgeonMatcher child : childMatcher) {
        Tree node = child.evaluate(tree, tregex);
        node.label().setValue(node.label().value() + coindexationIntroductionString + newIndex);
      }
      return tree;
    }
  }
}
