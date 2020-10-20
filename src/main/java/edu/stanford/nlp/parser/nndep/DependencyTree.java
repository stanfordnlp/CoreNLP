package edu.stanford.nlp.parser.nndep;

import java.util.*;

/**
 * Represents a partial or complete dependency parse of a sentence, and
 * provides convenience methods for analyzing the parse.
 *
 * @author Danqi Chen
 */
class DependencyTree {

  int n;
  final List<Integer> head;
  final List<String> label;
  private int counter;

  public DependencyTree() {
    n = 0;
    head = new ArrayList<>();
    head.add(Config.NONEXIST);
    label = new ArrayList<>();
    label.add(Config.UNKNOWN);
  }

  public DependencyTree(DependencyTree tree) {
    n = tree.n;
    head = new ArrayList<>(tree.head);
    label = new ArrayList<>(tree.label);
  }

  /**
   * Add the next token to the parse.
   *
   * @param h Head of the next token
   * @param l Dependency relation label between this node and its head
   */
  public void add(int h, String l) {
    ++n;
    head.add(h);
    label.add(l);
  }

  /**
   * Establish a labeled dependency relation between the two given
   * nodes.
   *
   * @param k Index of the dependent node
   * @param h Index of the head node
   * @param l Label of the dependency relation
   */
  public void set(int k, int h, String l) {
    head.set(k, h);
    label.set(k, l);
  }

  public int getHead(int k) {
    if (k <= 0 || k > n)
      return Config.NONEXIST;
    else
      return head.get(k);
  }

  public String getLabel(int k) {
    if (k <= 0 || k > n)
      return Config.NULL;
    else
      return label.get(k);
  }

  /**
   * Get the index of the node which is the root of the parse (i.e.,
   * that node which has the ROOT node as its head).
   */
  public int getRoot() {
    for (int k = 1; k <= n; ++k)
      if (getHead(k) == 0)
        return k;
    return 0;
  }

  /**
   * Check if this parse has only one root.
   */
  public boolean isSingleRoot() {
    int roots = 0;
    for (int k = 1; k <= n; ++k)
      if (getHead(k) == 0)
        roots = roots + 1;
    return (roots == 1);
  }

  // check if the tree is legal, O(n)
  public boolean isTree() {
    List<Integer> h = new ArrayList<>();
    h.add(-1);
    for (int i = 1; i <= n; ++i) {
      if (getHead(i) < 0 || getHead(i) > n)
        return false;
      h.add(-1);
    }
    for (int i = 1; i <= n; ++i) {
      int k = i;
      while (k > 0) {
        if (h.get(k) >= 0 && h.get(k) < i) break;
        if (h.get(k) == i)
          return false;
        h.set(k, i);
        k = getHead(k);
      }
    }
    return true;
  }

  // check if the tree is projective, O(n^2)
  public boolean isProjective() {
    if (!isTree())
      return false;
    counter = -1;
    return visitTree(0);
  }

  // Inner recursive function for checking projectivity of tree
  private boolean visitTree(int w) {
    for (int i = 1; i < w; ++i)
      if (getHead(i) == w && visitTree(i) == false)
        return false;
    counter = counter + 1;
    if (w != counter)
      return false;
    for (int i = w + 1; i <= n; ++i)
      if (getHead(i) == w && visitTree(i) == false)
        return false;
    return true;
  }

  // TODO properly override equals, hashCode?
  public boolean equal(DependencyTree t) {
    if (t.n != n)
      return false;
    for (int i = 1; i <= n; ++i) {
      if (getHead(i) != t.getHead(i))
        return false;
      if (!getLabel(i).equals(t.getLabel(i)))
        return false;
    }
    return true;
  }

  public void print() {
    for (int i = 1; i <= n; ++i)
      System.out.println(i + " " + getHead(i) + " " + getLabel(i));
    System.out.println();
  }

}