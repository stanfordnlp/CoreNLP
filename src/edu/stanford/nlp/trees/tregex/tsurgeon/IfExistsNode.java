package edu.stanford.nlp.trees.tregex.tsurgeon;

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
  public Tree evaluate(Tree t, TregexMatcher m) {
    if (invert ^ (m.getNode(name) != null)) {
      for (TsurgeonPattern child : children) {
        child.evaluate(t, m);
      }
    }
    return t;
  }
}
