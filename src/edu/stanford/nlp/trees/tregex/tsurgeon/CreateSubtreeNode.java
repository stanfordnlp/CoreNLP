package edu.stanford.nlp.trees.tregex.tsurgeon;

import java.util.List;
import java.util.Map;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.util.Generics;

/**
 * Given the start and end children of a particular node, takes all
 * children between start and end (including the endpoints) and
 * combines them in a new node with the given label.
 *
 * @author John Bauer
 */
public class CreateSubtreeNode extends TsurgeonPattern {
  private AuxiliaryTree auxTree;

  public CreateSubtreeNode(TsurgeonPattern start, AuxiliaryTree tree) {
    this(start, null, tree);
  }

  public CreateSubtreeNode(TsurgeonPattern start, TsurgeonPattern end, AuxiliaryTree tree) {
    super("combineSubtrees",
      (end == null) ? new TsurgeonPattern[] { start } : new TsurgeonPattern[] { start, end });

    this.auxTree = tree;
    findFoot();
  }

  /**
   * We want to support a command syntax where a simple node label can
   * be given (i.e., without using a tree literal).
   *
   * Check if this syntax is being used, and simulate a foot if so.
   */
  private void findFoot() {
    if (auxTree.foot == null) {
      if (!auxTree.tree.isLeaf())
        throw new TsurgeonParseException("No foot node found for " + auxTree);

      // Pretend this leaf is a foot node
      auxTree.foot = auxTree.tree;
    }
  }

  @Override
  public TsurgeonMatcher matcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
    return new Matcher(newNodeNames, coindexer);
  }

  private class Matcher extends TsurgeonMatcher {
    public Matcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
      super(CreateSubtreeNode.this, newNodeNames, coindexer);
    }

    /**
     * Combines all nodes between start and end into one subtree, then
     * replaces those nodes with the new subtree in the corresponding
     * location under parent
     */
    @Override
    public Tree evaluate(Tree tree, TregexMatcher tregex) {
      Tree startChild = childMatcher[0].evaluate(tree, tregex);
      Tree endChild = (childMatcher.length == 2) ? childMatcher[1].evaluate(tree, tregex) : startChild;

      Tree parent = startChild.parent(tree);

      // sanity check
      if (parent != endChild.parent(tree)) {
        throw new TsurgeonRuntimeException("Parents did not match for trees when applied to " + this);
      }
      
      AuxiliaryTree treeCopy = auxTree.copy(this, tree.treeFactory(), tree.label().labelFactory());

      // Collect all the children of the parent of the node we care
      // about.  If the child is one of the nodes we care about, or
      // between those two nodes, we add it to a list of inner children.
      // When we reach the second endpoint, we turn that list of inner
      // children into a new node using the newly created label.  All
      // other children are kept in an outer list, with the new node
      // added at the appropriate location.
      List<Tree> children = Generics.newArrayList();
      List<Tree> innerChildren = Generics.newArrayList();
      boolean insideSpan = false;
      for (Tree child : parent.children()) {
        if (child == startChild || child == endChild) {
          if (!insideSpan && startChild != endChild) {
            insideSpan = true;
            innerChildren.add(child);
          } else {
            insideSpan = false;
            innerChildren.add(child);

            // All children have been collected; place these beneath the foot of the auxiliary tree
            treeCopy.foot.setChildren(innerChildren);
            children.add(treeCopy.tree);
          }
        } else if (insideSpan) {
          innerChildren.add(child);
        } else {
          children.add(child);
        }
      }

      parent.setChildren(children);

      return tree;
    }
  }
}
