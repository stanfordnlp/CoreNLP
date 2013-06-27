package edu.stanford.nlp.trees.tregex.tsurgeon;

import java.util.List;
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
  public Tree evaluate(Tree t, TregexMatcher m) {
    boolean prunedWholeTree = false;
    for(TsurgeonPattern child : children) {
      final Tree nodeToPrune = child.evaluate(t,m);
      if(pruneHelper(t,nodeToPrune) == null)
        prunedWholeTree = true;
    }
    return prunedWholeTree ? null : t;
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
