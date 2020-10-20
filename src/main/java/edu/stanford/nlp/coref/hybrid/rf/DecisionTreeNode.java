package edu.stanford.nlp.coref.hybrid.rf;

import java.io.Serializable;

public class DecisionTreeNode implements Serializable {
  private static final long serialVersionUID = 8566766017320577273L;

  int idx;   // if not leaf, feature index. if leaf, idx=1 -> true, idx=0 -> false.
  float split;  // if not leaf, split point. if leaf, true probability.
  DecisionTreeNode[] children;    // go left if value is less than split

  DecisionTreeNode() {
    idx = -1;
    split = Float.NaN;
    children = null;
  }

  public DecisionTreeNode(int label, float prob) {
    this();
    idx = label;
    split = prob;
  }

  public DecisionTreeNode(int idx, float split, DecisionTreeNode[] children) {
    this.idx = idx;
    this.split = split;
    this.children = children;
  }

  public boolean isLeaf() {
    return (children==null);
  }
}
