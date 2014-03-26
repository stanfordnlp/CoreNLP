package edu.stanford.nlp.trees.tregex.tsurgeon;

import java.util.List;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.util.ArrayUtils;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.Function;

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

  private static final Function<AuxiliaryTree, HoldTreeNode> convertAuxiliaryToHold = new Function<AuxiliaryTree, HoldTreeNode>() {
      public HoldTreeNode apply(AuxiliaryTree t) { return new HoldTreeNode(t); }
    };

  @Override
  public Tree evaluate(Tree t, TregexMatcher m) {
    Tree oldNode = children[0].evaluate(t,m);
    if (oldNode==t) {
      if (children.length > 2) {
        throw new TsurgeonRuntimeException("Attempted to replace a root node with more than one node, unable to proceed");
      }
      return children[1].evaluate(t, m);
    }
    Tree parent = oldNode.parent(t);
    int i = parent.objectIndexOf(oldNode);
    parent.removeChild(i);
    for (int j = 1; j < children.length; ++j) {
      Tree newNode = children[j].evaluate(t, m);
      parent.insertDtr(newNode.deepCopy(), i + j - 1);
    }
    return t;
  }
}
