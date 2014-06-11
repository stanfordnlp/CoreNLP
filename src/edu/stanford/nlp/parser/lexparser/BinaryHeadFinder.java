package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.Tree;

public class BinaryHeadFinder implements HeadFinder {
  private static final long serialVersionUID = 4794072338791804184L;

  private final HeadFinder fallbackHF;

  public BinaryHeadFinder() {
    this(null);
  }

  public BinaryHeadFinder(HeadFinder fallbackHF) {
    this.fallbackHF = fallbackHF;
  }

  /**
   * Determine which daughter of the current parse tree is the head.
   * It assumes that the daughters already have had their heads
   * determined. Another method has to do the tree walking.
   *
   * @param t The parse tree to examine the daughters of
   * @return The parse tree that is the head.  The convention has been
   *         that this returns <code>null</code> if no head is found.
   *         But maybe it should throw an exception?
   */
  public Tree determineHead(Tree t) {
    Tree result = determineBinaryHead(t);
    if (result == null && fallbackHF != null) {
      result = fallbackHF.determineHead(t);
    }
    if (result != null) {
      return result;
    }
    throw new IllegalStateException("BinaryHeadFinder: unexpected tree: " + t);
  }
  
  public Tree determineHead(Tree t, Tree parent){
    Tree result = determineBinaryHead(t);
    if (result == null && fallbackHF != null) {
      result = fallbackHF.determineHead(t, parent);
    }
    if (result != null) {
      return result;
    }
    throw new IllegalStateException("BinaryHeadFinder: unexpected tree: " + t);
  }

  private Tree determineBinaryHead(Tree t) {
    if (t.numChildren() == 1) {
      return t.firstChild();
    } else {
      String lval = t.firstChild().label().value();
      if (lval != null && lval.startsWith("@")) {
        return t.firstChild();
      } else {
        String rval = t.lastChild().label().value();
        if (rval.startsWith("@") || rval.equals(Lexicon.BOUNDARY_TAG)) {
          return t.lastChild();
        }
      }
    }
    return null;
  }
  
} // end static class BinaryHeadFinder
