package edu.stanford.nlp.trees.tregex.tsurgeon;

import java.util.Map;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;

/**
 * @author Roger Levy (rog@stanford.edu)
 */
class FetchNode extends TsurgeonPattern {

  public FetchNode(String nodeName) {
    super(nodeName, TsurgeonPattern.EMPTY_TSURGEON_PATTERN_ARRAY);
  }


  @Override
  public TsurgeonMatcher matcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
    return new Matcher(newNodeNames, coindexer);
  }

  private class Matcher extends TsurgeonMatcher {
    public Matcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
      super(FetchNode.this, newNodeNames, coindexer);
    }

    @Override
    public Tree evaluate(Tree tree, TregexMatcher tregex) {
      Tree result = newNodeNames.get(label);
      if (result == null) {
        result = tregex.getNode(label);
      }
      if (result == null) {
        System.err.println("Warning -- null node fetched by Tsurgeon operation for node: " + this +
                           " (either no node labeled this, or the labeled node didn't match anything)");
      }
      return result;
    }
  }
}
