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

  public IfExistsNode(String name, TsurgeonPattern ... children) {
    super("if exists " + name, children);
    this.name = name;
  }

  @Override
  public Tree evaluate(Tree t, TregexMatcher m) {
    if (m.getNode(name) != null) {
      for (TsurgeonPattern child : children) {
        child.evaluate(t, m);
      }
    }
    return t;
  }
}
