package old.edu.stanford.nlp.trees.tregex.tsurgeon;

import old.edu.stanford.nlp.trees.tregex.TregexMatcher;
import old.edu.stanford.nlp.trees.Tree;

/**
 * @author Roger Levy (rog@nlp.stanford.edu)
 */
public class CoindexNodes extends TsurgeonPattern {

  private static String coindexationIntroductionString = "-";

  public CoindexNodes(TsurgeonPattern[] children) {
    super("coindex", children);
  }

  @Override
  public Tree evaluate(Tree t, TregexMatcher m) {
    int newIndex = root.coindexer.generateIndex();
    for(TsurgeonPattern child : children) {
      Tree node = child.evaluate(t,m);
      node.label().setValue(node.label().value() + coindexationIntroductionString + newIndex);
    }
    return t;
  }

}
