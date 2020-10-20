package edu.stanford.nlp.classify;

import java.util.*;

import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.*;
import edu.stanford.nlp.util.CollectionValuedMap;
import edu.stanford.nlp.util.Generics;

/**
 * A simple k-NN classifier, with the options of using unit votes, or weighted votes (by 
 * similarity value).  Use the <code>KNNClassifierFactory</code> class to train and instantiate
 * a new classifier.
 * 
 * NOTE: partially generified, waiting for final generification of classifiers package.
 * @author Eric Yeh
 *
 * @param <K> Class label type
 * @param <V> Feature vector dimension type
 */
public class KNNClassifier<K,V> implements Classifier<K, V> {
  /**
   * 
   */
  private static final long serialVersionUID = 7115357548209007944L;
  private boolean weightedVotes = false; // whether this is a weighted vote (by sim), or not
  private CollectionValuedMap<K, Counter<V>> instances = new CollectionValuedMap<>();
  private Map<Counter<V>, K> classLookup = Generics.newHashMap();
  private boolean l2Normalize = false;
  int k = 0;

  public Collection<K> labels() {
    return classLookup.values();
  }

  protected KNNClassifier(int k, boolean weightedVotes, boolean l2Normalize) { 
    this.k = k;
    this.weightedVotes = weightedVotes;
    this.l2Normalize = l2Normalize;
  }

  protected void addInstances(Collection<RVFDatum<K, V>> datums) {
    for (RVFDatum<K, V> datum : datums) {
      K label = datum.label();
      Counter<V> vec = datum.asFeaturesCounter();
      instances.add(label, vec);
      classLookup.put(vec, label);
    }
  }

  /**
   * NOTE: currently does not support standard Datums, only RVFDatums.
   */
  public K classOf(Datum<K, V> example) {
    if (example instanceof RVFDatum<?,?>) {
      ClassicCounter<K> scores = scoresOf(example);
      return Counters.toSortedList(scores).get(0);
    } else {
      return null; 
    }

  }

  /**
   * Given an instance to classify, scores and returns
   * score by class.
   * 
   * NOTE: supports only RVFDatums
   */
  public ClassicCounter<K> scoresOf(Datum<K, V> datum) {
    if (datum instanceof RVFDatum<?,?>) {
      RVFDatum<K, V> vec = (RVFDatum<K, V>) datum;

      if (l2Normalize) {
        ClassicCounter<V> featVec = new ClassicCounter<>(vec.asFeaturesCounter());
        Counters.normalize(featVec);
        vec = new RVFDatum<>(featVec);
      }

      ClassicCounter<Counter<V>> scores = new ClassicCounter<>();
      for (Counter<V> instance : instances.allValues()) {
        scores.setCount(instance, Counters.cosine(vec.asFeaturesCounter(), instance)); // set entry, for given instance and score
      }
      List<Counter<V>> sorted = Counters.toSortedList(scores);
      ClassicCounter<K> classScores = new ClassicCounter<>();
      for (int i=0;i<k && i<sorted.size(); i++) {
        K label = classLookup.get(sorted.get(i));
        double count= 1.0;
        if (weightedVotes) {
          count = scores.getCount(sorted.get(i));
        }
        classScores.incrementCount(label, count);
      }
      return classScores;
    } else {
      return null;
    }
  }

  // Quick little sanity check
  public static void main(String[] args) {
    Collection<RVFDatum<String, String>> trainingInstances = new ArrayList<>();
    {
      ClassicCounter<String> f1 = new ClassicCounter<>();
      f1.setCount("humidity", 5.0);
      f1.setCount("temperature", 35.0);
      trainingInstances.add(new RVFDatum<>(f1, "rain"));
    }

    {
      ClassicCounter<String> f1 = new ClassicCounter<>();
      f1.setCount("humidity", 4.0);
      f1.setCount("temperature", 32.0);
      trainingInstances.add(new RVFDatum<>(f1, "rain"));
    }

    {
      ClassicCounter<String> f1 = new ClassicCounter<>();
      f1.setCount("humidity", 6.0);
      f1.setCount("temperature", 30.0);
      trainingInstances.add(new RVFDatum<>(f1, "rain"));
    }

    {
      ClassicCounter<String> f1 = new ClassicCounter<>();
      f1.setCount("humidity", 2.0);
      f1.setCount("temperature", 33.0);
      trainingInstances.add(new RVFDatum<>(f1, "dry"));
    }

    {
      ClassicCounter<String> f1 = new ClassicCounter<>();
      f1.setCount("humidity", 1.0);
      f1.setCount("temperature", 34.0);
      trainingInstances.add(new RVFDatum<>(f1, "dry"));
    }

    KNNClassifier<String, String> classifier = new KNNClassifierFactory<String, String>(3, false, true).train(trainingInstances);

    {
      ClassicCounter<String> f1 = new ClassicCounter<>();
      f1.setCount("humidity", 2.0);
      f1.setCount("temperature", 33.0);
      RVFDatum<String, String> testVec = new RVFDatum<>(f1);
      System.out.println(classifier.scoresOf(testVec));
      System.out.println(classifier.classOf(testVec));
    }
  }

}
