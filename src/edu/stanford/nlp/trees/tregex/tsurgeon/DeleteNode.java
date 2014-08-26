package edu.stanford.nlp.trees.tregex.tsurgeon;

import java.util.List;
import java.util.Map;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Trees;
import edu.stanford.nlp.trees.tregex.TregexMatcher;

/**
 * @author Roger Levy (rog@stanford.edu)
 */
class DeleteNode extends TsurgeonPattern {

  public DeleteNode(TsurgeonPattern[] children) {
    super("delete", children);
  }

  public DeleteNode(List<TsurgeonPattern> children) {
    this(children.toArray(new TsurgeonPattern[children.size()]));
  }

  @Override
  public TsurgeonMatcher matcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
    return new Matcher(newNodeNames, coindexer);
  }

  private class Matcher extends TsurgeonMatcher {
    public Matcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
      super(DeleteNode.this, newNodeNames, coindexer);
    }

    @Override
    public Tree evaluate(Tree tree, TregexMatcher tregex) {
      Tree result = tree;
      for (TsurgeonMatcher child : childMatcher) {
        Tree nodeToDelete = child.evaluate(tree, tregex);
        if (nodeToDelete == tree) {
          result = null;
        }
        Tree parent = nodeToDelete.parent(tree);
        parent.removeChild(Trees.objectEqualityIndexOf(parent,nodeToDelete));
      }
      return result;
    }
  }

}
