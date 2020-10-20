package edu.stanford.nlp.trees.tregex.tsurgeon; 

import java.util.Map;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * @author Roger Levy (rog@stanford.edu)
 */
public class FetchNode extends TsurgeonPattern  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(FetchNode.class);

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
        log.warn("Null node fetched by Tsurgeon operation for node: " + label +
                           " (either no node labeled this, or the labeled node didn't match anything)");
      }
      return result;
    }

  } // end class Matcher

}
