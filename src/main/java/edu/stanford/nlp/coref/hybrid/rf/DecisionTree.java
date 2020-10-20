package edu.stanford.nlp.coref.hybrid.rf;

import java.io.Serializable;

import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Index;

public class DecisionTree implements Serializable {
  private static final long serialVersionUID = -4198470422641238244L;

  public DecisionTreeNode root;
  public Index<String> featureIndex;

  public DecisionTree(Index<String> featureIndex) {
    this.featureIndex = featureIndex;
    this.root = null;
  }

  public double probabilityOfTrue(RVFDatum<Boolean, String> datum) {
    return probabilityOfTrue(datum.asFeaturesCounter());
  }
  public double probabilityOfTrue(Counter<String> features) {
    DecisionTreeNode cur = root;

    while(!cur.isLeaf()) {
      double value = features.getCount(featureIndex.get(cur.idx));
      cur = (value < cur.split)? cur.children[0] : cur.children[1];
    }

    return (cur.split);    // at the leaf node, idx represents true or false. 1: true, 0: false, split represents probability of true.
  }
}
