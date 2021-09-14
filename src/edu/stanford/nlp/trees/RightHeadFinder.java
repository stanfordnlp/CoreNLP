package edu.stanford.nlp.trees;


/**
 * HeadFinder that always returns the rightmost daughter as head.
 *<br>
 * Useful for languages which have a mostly right branching structure
 * where we haven't done a ton of work figuring out how to find heads.
 *<br>
 * In particular, a conversation with Dora Demszky made it sound like
 * Hungarian would get better results with a RightHeadFinder instead
 * of LeftHeadFinder
 *
 * @author John Bauer
 */
public class RightHeadFinder implements HeadFinder {

  private static final long serialVersionUID = 127638412457653L;

  public Tree determineHead(Tree t) {
    if (t.isLeaf()) {
      return null;
    } else {
      int child = t.numChildren() - 1;
      return t.children()[child];
    }
  }

  public Tree determineHead(Tree t, Tree parent) {
    return determineHead(t);
  }

}
