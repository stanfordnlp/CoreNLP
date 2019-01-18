package edu.stanford.nlp.classify;

import java.util.*;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.stats.*;
import edu.stanford.nlp.util.CollectionValuedMap;

/**
 * This constructs trained {@code KNNClassifier} objects, given
 * sets of RVFDatums, or Counters (dimensions are identified by the keys).
 */
public class KNNClassifierFactory<K, V> {

  private int k; // = 0;
  private boolean weightedVotes; // = false;
  private boolean l2NormalizeVectors; // = false;

  /**
   * Creates a new factory that generates K-NN classifiers with the given k-value, and
   * if the votes are weighted by their similarity score, or unit value.
   */
  public KNNClassifierFactory(int k, boolean weightedVotes, boolean l2NormalizeVectors) {
    this.k = k;
    this.weightedVotes = weightedVotes;
    this.l2NormalizeVectors = l2NormalizeVectors;
  }

  /**
   * Given a set of labeled RVFDatums, treats each as an instance vector of that
   * label and adds it to the examples used for classification.
   *
   * NOTE: l2NormalizeVectors is NOT applied here.
   */
  public KNNClassifier<K,V> train(Collection<RVFDatum<K, V>> instances) {
    KNNClassifier<K, V> classifier = new KNNClassifier<>(k, weightedVotes, l2NormalizeVectors);
    classifier.addInstances(instances);
    return classifier;
  }

  /**
   * Given a set of vectors, and a mapping from each vector to its class label, 
   * generates the sets of instances used to perform classifications and returns
   * the corresponding K-NN classifier.
   *
   * NOTE: if l2NormalizeVectors is T, creates a copy and applies L2Normalize to it.
   */
  public KNNClassifier<K,V> train(Collection<Counter<V>> vectors, Map<V, K> labelMap) {
    KNNClassifier<K, V> classifier = new KNNClassifier<>(k, weightedVotes, l2NormalizeVectors);
    Collection<RVFDatum<K, V>> instances = new ArrayList<>();
    for (Counter<V> vector : vectors) {
      K label = labelMap.get(vector);
      RVFDatum<K, V> datum;
      if (l2NormalizeVectors) { 
        datum = new RVFDatum<>(Counters.L2Normalize(new ClassicCounter<>(vector)), label);
      } else {
        datum = new RVFDatum<>(vector, label);
      }
      instances.add(datum);
    }

    classifier.addInstances(instances);
    return classifier;
  }

  /**
   * Given a CollectionValued Map of vectors, treats outer key as label for each
   * set of inner vectors.
   * NOTE: if l2NormalizeVectors is T, creates a copy of each vector and applies 
   * l2Normalize to it.
   */
  public KNNClassifier<K,V> train(CollectionValuedMap<K, Counter<V>> vecBag) {
    KNNClassifier<K, V> classifier = new KNNClassifier<>(k, weightedVotes, l2NormalizeVectors);
    Collection<RVFDatum<K, V>> instances = new ArrayList<>();
    for (K label : vecBag.keySet()) {
      RVFDatum<K, V> datum;
      for (Counter<V> vector : vecBag.get(label)) {
        if (l2NormalizeVectors) {
          datum = new RVFDatum<>(Counters.L2Normalize(new ClassicCounter<>(vector)), label);
        }  else {
         datum = new RVFDatum<>(vector, label);
        }
        instances.add(datum);
      }
    }

    classifier.addInstances(instances);
    return classifier;
  }

}
