package edu.stanford.nlp.coref.hybrid.rf;

import java.io.Serializable;

import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Index;

public class RandomForest implements Serializable {
  private static final long serialVersionUID = -2736377471905671276L;

  public final DecisionTree[] trees;
  public final Index<String> featureIndex;

  public RandomForest(Index<String> featureIndex, int numTrees) {
    this.featureIndex = featureIndex;
    this.trees = new DecisionTree[numTrees];
  }

  public double probabilityOfTrue(RVFDatum<Boolean,String> datum) {
    return probabilityOfTrue(datum.asFeaturesCounter());
  }
  public double probabilityOfTrue(Counter<String> features) {
    double probTrue = 0;
    for (DecisionTree tree : trees) {
      probTrue += tree.probabilityOfTrue(features);
    }
    return probTrue / trees.length;
  }
}
