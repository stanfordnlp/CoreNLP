package edu.stanford.nlp.trees.tregex.tsurgeon; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.Map;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;

/** Adjoin in a tree (like in TAG), but retain the target of adjunction as the foot of the auxiliary tree.
 * @author Roger Levy (rog@nlp.stanford.edu)
 */
public class AdjoinToFootNode extends AdjoinNode  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(AdjoinToFootNode.class);

  public AdjoinToFootNode(AuxiliaryTree t, TsurgeonPattern p) {
    super("adjoinF", t, p);
  }

  @Override
  public TsurgeonMatcher matcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
    return new Matcher(newNodeNames, coindexer);
  }

  private class Matcher extends TsurgeonMatcher {
    public Matcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
      super(AdjoinToFootNode.this, newNodeNames, coindexer);
    }

    @Override
    public Tree evaluate(Tree tree, TregexMatcher tregex) {
      // find match and get its parent
      Tree targetNode = childMatcher[0].evaluate(tree, tregex);
      Tree parent = targetNode.parent(tree);
      // substitute original node for foot of auxiliary tree.  Foot node is ignored
      AuxiliaryTree ft = adjunctionTree().copy(this, tree.treeFactory(), tree.label().labelFactory());
      // log.info("ft=" + ft + "; ft.foot=" + ft.foot + "; ft.tree=" + ft.tree);
      Tree parentOfFoot = ft.foot.parent(ft.tree);
      if (parentOfFoot == null) {
        log.info("Warning: adjoin to foot for depth-1 auxiliary tree has no effect.");
        return tree;
      }
      int i = parentOfFoot.objectIndexOf(ft.foot);
      if (parent==null) {
        parentOfFoot.setChild(i,targetNode);
        return ft.tree;
      } else {
        int j = parent.objectIndexOf(targetNode);
        parent.setChild(j,ft.tree);
        parentOfFoot.setChild(i,targetNode);
        return tree;
      }
    }
  }

}
