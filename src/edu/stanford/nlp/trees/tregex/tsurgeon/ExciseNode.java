package edu.stanford.nlp.trees.tregex.tsurgeon;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Trees;
import edu.stanford.nlp.trees.tregex.TregexMatcher;

/** Excises all nodes from the top to the bottom, and puts all the children of bottom node in where the top was.
 * @author Roger Levy (rog@stanford.edu)
 */
class ExciseNode extends TsurgeonPattern {

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
  public Tree evaluate(Tree t, TregexMatcher m) {
    Tree topNode = children[0].evaluate(t,m);
    Tree bottomNode = children[1].evaluate(t,m);
    if(Tsurgeon.verbose) {
      System.err.println("Excising...original tree:");
      t.pennPrint(System.err);
      System.err.println("top: " + topNode + "\nbottom:" + bottomNode);
    }
    if(topNode==t)
      return null;
    Tree parent = topNode.parent(t);
    if(Tsurgeon.verbose)
      System.err.println("Parent: " + parent);
    int i = Trees.objectEqualityIndexOf(parent,topNode);
    parent.removeChild(i);
    for(Tree child : bottomNode.children()) {
      parent.addChild(i,child);
      i++;
    }
    if(Tsurgeon.verbose)
      t.pennPrint(System.err);
    return t;
  }
}
