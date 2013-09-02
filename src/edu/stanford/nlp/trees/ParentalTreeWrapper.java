package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.Label;

/**
 * A wrapper class for a regular tree that looks like a Tree, but adds
 * parent pointers to the nodes.  Calls to parent() or parent(root)
 * return the cached parent pointer, and other calls are passed
 * through to the underlying Tree.  This is useful in the Tregex
 * system, where it greatly reduces the time spent searching for
 * parent nodes.
 * <br>
 * TODO: only those calls used by the Tregex system get passed along
 * to the underlying Tree.  Other users may want to expand on this.
 *
 * @author John Bauer
 */
public class ParentalTreeWrapper extends Tree {
  private final Tree tree;
  private final Tree parent;
  private final Tree[] children;

  public ParentalTreeWrapper(Tree tree) {
    this(tree, null);
  }

  public ParentalTreeWrapper(Tree tree, Tree parent) {
    this.tree = tree;
    this.parent = parent;
    Tree[] originalChildren = tree.children();
    this.children = new Tree[originalChildren.length];
    for (int i = 0; i < originalChildren.length; ++i) {
      Tree child = new ParentalTreeWrapper(originalChildren[i], this);
      this.children[i] = child;
    }
  }

  @Override
  public Tree[] children() { return children; }
  
  @Override
  public TreeFactory treeFactory() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Tree parent() {
    return parent;
  }

  @Override
  public Tree parent(Tree root) {
    return parent;
  }

  public Tree getBackingTree() { 
    return tree;
  }

  public Label label() {
    return tree.label();
  }

  public String value() {
    return tree.value();
  }

  public Tree findContainingNode(Tree node) {
    if (this == node || this.tree == node) {
      return this;
    }
    for (Tree child : children) {
      if (!(child instanceof ParentalTreeWrapper)) {
        throw new AssertionError();
      }
      Tree result = ((ParentalTreeWrapper) child).findContainingNode(node);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  private static final long serialVersionUID = 1L;
}