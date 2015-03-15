package edu.stanford.nlp.hcoref.rf;

import java.io.Serializable;

import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;

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
    int nThreads = Runtime.getRuntime().availableProcessors();
    MulticoreWrapper<Pair<DecisionTree, Counter<String>>, Double> wrapper = new MulticoreWrapper<Pair<DecisionTree, Counter<String>>, Double>(
        nThreads, new ThreadsafeProcessor<Pair<DecisionTree, Counter<String>>, Double>() {
          @Override
          public Double process(Pair<DecisionTree, Counter<String>> input) {
            try {
              return input.first.probabilityOfTrue(input.second);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          }
          
          @Override
          public ThreadsafeProcessor<Pair<DecisionTree, Counter<String>>, Double> newInstance() {
            return this;
          }
        });

    double probTrue = 0;
    for (DecisionTree tree : trees) {
      wrapper.put(Pair.makePair(tree, features));
      while (wrapper.peek()) {
        probTrue += wrapper.poll();
      }
    }
    wrapper.join();
    while (wrapper.peek()) {
      probTrue += wrapper.poll();
    }
    return probTrue / trees.length;
  }
}
