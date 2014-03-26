package edu.stanford.nlp.trees.tregex.tsurgeon;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.util.Generics;

import java.util.Map;

/**
 * @author Roger Levy (rog@nlp.stanford.edu)
 */
class TsurgeonPatternRoot extends TsurgeonPattern {

  // TODO: both of these variables prevent Tsurgeon from being used in
  // a threadsafe manner.  They should be factored into a Matcher
  // object the same way regex, tregex, semgrex all work
  CoindexationGenerator coindexer;
  Map<String, Tree> newNodeNames;

  public TsurgeonPatternRoot(TsurgeonPattern child) {
    this(new TsurgeonPattern[] { child });
  }

  public TsurgeonPatternRoot(TsurgeonPattern[] children) {
    super("operations: ", children);
    setRoot(this);
  }

  /**
   * If one of the children is a CoindexNodes (or something else that
   * wants coindexing), it can call this at the time of setRoot()
   */
  void setCoindexes() {
    coindexer = new CoindexationGenerator();
  }

  /**
   * returns null if one of the surgeries eliminates the tree entirely.  The
   * operated-on tree is not to be trusted in this instance.
   */
  @Override
  public Tree evaluate(Tree t, TregexMatcher m) {
    // TODO: not threadsafe
    newNodeNames = Generics.newHashMap();
    if (coindexer != null) {
      coindexer.setLastIndex(t);
    }

    for (TsurgeonPattern child : children) {
      t = child.evaluate(t, m);
      if (t == null) {
        return null;
      }
    }
    return t;
  }
}
