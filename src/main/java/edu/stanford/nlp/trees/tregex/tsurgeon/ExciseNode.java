package edu.stanford.nlp.trees.tregex.tsurgeon; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.Map;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Trees;
import edu.stanford.nlp.trees.tregex.TregexMatcher;

/** Excises all nodes from the top to the bottom, and puts all the children of bottom node in where the top was.
 * @author Roger Levy (rog@stanford.edu)
 */
public class ExciseNode extends TsurgeonPattern  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(ExciseNode.class);

  /**
   * Top should evaluate to a node that dominates bottom, but this is not checked!
   */
  public ExciseNode(TsurgeonPattern top, TsurgeonPattern bottom) {
    super("excise", new TsurgeonPattern[] { top, bottom });
  }

  /**
   * Excises only the directed node.
   */
  public ExciseNode(TsurgeonPattern node) {
    super("excise", new TsurgeonPattern[] { node,node });
  }

  @Override
  public TsurgeonMatcher matcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
    return new Matcher(newNodeNames, coindexer);
  }

  private class Matcher extends TsurgeonMatcher {
    public Matcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
      super(ExciseNode.this, newNodeNames, coindexer);
    }

    @Override
    public Tree evaluate(Tree tree, TregexMatcher tregex) {
      Tree topNode = childMatcher[0].evaluate(tree, tregex);
      Tree bottomNode = childMatcher[1].evaluate(tree, tregex);
      if(Tsurgeon.verbose) {
        log.info("Excising...original tree:");
        tree.pennPrint(System.err);
        log.info("top: " + topNode + "\nbottom:" + bottomNode);
      }
      if (topNode == tree) {
        if (bottomNode.children().length == 1) {
          return bottomNode.children()[0];
        } else {
          return null;
        }
      }
      Tree parent = topNode.parent(tree);
      if(Tsurgeon.verbose)
        log.info("Parent: " + parent);
      int i = Trees.objectEqualityIndexOf(parent,topNode);
      parent.removeChild(i);
      for(Tree child : bottomNode.children()) {
        parent.addChild(i,child);
        i++;
      }
      if(Tsurgeon.verbose)
        tree.pennPrint(System.err);
      return tree;
    }
  }
}
