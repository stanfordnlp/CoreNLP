package edu.stanford.nlp.trees.tregex.tsurgeon;

import java.util.List;
import java.util.Map;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Trees;
import edu.stanford.nlp.trees.tregex.TregexMatcher;

/**  Pruning differs from deleting in that if a non-terminal node winds up having no children, it is pruned as well.
 * @author Roger Levy (rog@nlp.stanford.edu)
 */
class PruneNode extends TsurgeonPattern {

  public PruneNode(TsurgeonPattern[] children) {
    super("prune", children );
  }

  public PruneNode(List<TsurgeonPattern> children) {
    this(children.toArray(new TsurgeonPattern[children.size()]));
  }

  @Override
  public TsurgeonMatcher matcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
    return new Matcher(newNodeNames, coindexer);
  }

  private class Matcher extends TsurgeonMatcher {
    public Matcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
      super(PruneNode.this, newNodeNames, coindexer);
    }

    @Override
    public Tree evaluate(Tree tree, TregexMatcher tregex) {
      boolean prunedWholeTree = false;
      for(TsurgeonMatcher child : childMatcher) {
        final Tree nodeToPrune = child.evaluate(tree, tregex);
        if(pruneHelper(tree,nodeToPrune) == null)
          prunedWholeTree = true;
      }
      return prunedWholeTree ? null : tree;
    }
  }

  private static Tree pruneHelper(Tree root, Tree nodeToPrune) {
    if(nodeToPrune==root)
      return null;
    Tree parent = nodeToPrune.parent(root);
    parent.removeChild(Trees.objectEqualityIndexOf(parent,nodeToPrune));
    if(parent.children().length==0)
      return pruneHelper(root,parent);
    return root;
  }
}
