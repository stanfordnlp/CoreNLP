package edu.stanford.nlp.trees.tregex.tsurgeon;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Roger Levy (rog@nlp.stanford.edu)
 */
class FootedTree {
  Tree tree;
  Tree foot;

  public FootedTree(Tree tree, Tree foot) {
    this.tree = tree;
    this.foot = foot;
  }

  public FootedTree copy() {
    Pair<Tree,Tree> result = copyHelper(tree);
    if(result.second()==null)
      throw new RuntimeException("No foot node in copy!");
    return new FootedTree(result.first(),result.second());
  }

  private Pair<Tree,Tree> copyHelper(Tree node) {
    if(node.isLeaf()) {
      if(node == foot) {
        Tree clone = node.treeFactory().newTreeNode(node.label(),new ArrayList<Tree>(0));
        return new Pair<Tree,Tree>(clone,clone);
      }
      else
        return new Pair<Tree,Tree>(node.treeFactory().newLeaf(node.label().labelFactory().newLabel(node.label())),null);
    }
    Tree newFoot = null;
    List<Tree> newChildren = new ArrayList<Tree>(node.children().length);
    for(Tree child : node.children()) {
      Pair<Tree,Tree> newChild = copyHelper(child);
      newChildren.add(newChild.first());
      if(newChild.second() != null)
        newFoot = newChild.second();
    }
    return new Pair<Tree,Tree>(node.treeFactory().newTreeNode(node.label().labelFactory().newLabel(node.label()),newChildren),newFoot);
  }
}
