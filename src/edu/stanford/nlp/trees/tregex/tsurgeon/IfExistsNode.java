package edu.stanford.nlp.trees.tregex.tsurgeon;

import java.util.Map;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;

/**
 * Executes the give children only if the named Tregex node exists in
 * the TregexMatcher at match time (allows for OR relations or
 * optional relations)
 *
 * @author John Bauer (horatio@gmail.com)
 */
class IfExistsNode extends TsurgeonPattern {
  final String name;
  final boolean invert;

  public IfExistsNode(String name, boolean invert, TsurgeonPattern ... children) {
    super("if " + (invert ? "not " : "") + "exists " + name, children);
    this.name = name;
    this.invert = invert;
  }

  @Override
  public TsurgeonMatcher matcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
    return new Matcher(newNodeNames, coindexer);
  }

  private class Matcher extends TsurgeonMatcher {
    public Matcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
      super(IfExistsNode.this, newNodeNames, coindexer);
    }

    @Override
    public Tree evaluate(Tree tree, TregexMatcher tregex) {
      if (invert ^ (tregex.getNode(name) != null)) {
        for (TsurgeonMatcher child : childMatcher) {
          child.evaluate(tree, tregex);
        }
      }
      return tree;
    }
  }
}
