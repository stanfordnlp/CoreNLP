package edu.stanford.nlp.trees.tregex.tsurgeon;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.util.Generics;

import java.util.Map;

/**
 * @author Roger Levy (rog@nlp.stanford.edu)
 */
class TsurgeonPatternRoot extends TsurgeonPattern {

  public TsurgeonPatternRoot(TsurgeonPattern child) {
    this(new TsurgeonPattern[] { child });
  }

  public TsurgeonPatternRoot(TsurgeonPattern[] children) {
    super("operations: ", children);
    setRoot(this);
  }

  boolean coindexes = false;

  /**
   * If one of the children is a CoindexNodes (or something else that
   * wants coindexing), it can call this at the time of setRoot()
   */
  void setCoindexes() {
    coindexes = true;
  }

  @Override
  public TsurgeonMatcher matcher() {
    CoindexationGenerator coindexer = null;
    if (coindexes) {
      coindexer = new CoindexationGenerator();
    }
    return matcher(Generics.<String,Tree>newHashMap(), coindexer);
  }

  @Override
  public TsurgeonMatcher matcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
    return new Matcher(newNodeNames, coindexer);
  }


  private class Matcher extends TsurgeonMatcher {
    public Matcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
      super(TsurgeonPatternRoot.this, newNodeNames, coindexer);
    }

    /**
     * returns null if one of the surgeries eliminates the tree entirely.  The
     * operated-on tree is not to be trusted in this instance.
     */
    @Override
    public Tree evaluate(Tree tree, TregexMatcher tregex) {
      if (coindexer != null) {
        coindexer.setLastIndex(tree);
      }
      for (TsurgeonMatcher child : childMatcher) {
        tree = child.evaluate(tree, tregex);
        if (tree == null) {
          return null;
        }
      }
      return tree;
    }
  }
}
