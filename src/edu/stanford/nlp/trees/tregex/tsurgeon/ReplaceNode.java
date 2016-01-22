package edu.stanford.nlp.trees.tregex.tsurgeon;

import java.util.List;
import java.util.Map;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.util.ArrayUtils;
import edu.stanford.nlp.util.CollectionUtils;
import java.util.function.Function;

/**
 * @author Roger Levy (rog@stanford.edu)
 */
class ReplaceNode extends TsurgeonPattern {

  public ReplaceNode(TsurgeonPattern oldNode, TsurgeonPattern ... newNodes) {
    super("replace", ArrayUtils.concatenate(new TsurgeonPattern[] { oldNode }, newNodes));
  }

  public ReplaceNode(TsurgeonPattern oldNode, List<AuxiliaryTree> trees) {
    this(oldNode, CollectionUtils.transformAsList(trees, convertAuxiliaryToHold).toArray(new TsurgeonPattern[trees.size()]));
  }

  private static final Function<AuxiliaryTree, HoldTreeNode> convertAuxiliaryToHold = t -> new HoldTreeNode(t);

  @Override
  public TsurgeonMatcher matcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
    return new Matcher(newNodeNames, coindexer);
  }

  private class Matcher extends TsurgeonMatcher {
    public Matcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
      super(ReplaceNode.this, newNodeNames, coindexer);
    }

    @Override
    public Tree evaluate(Tree tree, TregexMatcher tregex) {
      Tree oldNode = childMatcher[0].evaluate(tree, tregex);
      if (oldNode==tree) {
        if (children.length > 2) {
          throw new TsurgeonRuntimeException("Attempted to replace a root node with more than one node, unable to proceed");
        }
        return childMatcher[1].evaluate(tree, tregex);
      }
      Tree parent = oldNode.parent(tree);
      int i = parent.objectIndexOf(oldNode);
      parent.removeChild(i);
      for (int j = 1; j < children.length; ++j) {
        Tree newNode = childMatcher[j].evaluate(tree, tregex);
        parent.insertDtr(newNode.deepCopy(), i + j - 1);
      }
      return tree;
    }
  }
}
