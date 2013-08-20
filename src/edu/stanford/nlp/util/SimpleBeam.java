package edu.stanford.nlp.util;

import java.util.*;

import edu.stanford.nlp.math.*;

public class SimpleBeam<T> implements Iterable<T> {

  T[] objects;
  double[] scores;

  /**
   * Adds the the object, replacing the worst thing if this is better than the worst.
   */
  public boolean add(T o, double s) {
    for (int i=0; i<scores.length; i++) {
      if (scores[i]<s) {
        objects[i] = o;
        scores[i] = s;
        return true;
      }
    }
    return false;
  }

  public Iterator<T> iterator() {
    return Arrays.asList(objects).iterator();
  }

  public double getScore(T object) {
    for (int i=0; i<objects.length; i++) {
      if (objects[i].equals(object)) {
        return scores[i];
      }
    }
    throw new RuntimeException();
  }

  public Pair<T,Double> getBest() {
    int bestIndex = ArrayMath.argmax(scores);
    return new Pair<T,Double>(objects[bestIndex],scores[bestIndex]);
  }

  public SimpleBeam (int size) {
    objects = ErasureUtils.<T>mkTArray(Object.class,size);
    scores = new double[size];
    Arrays.fill(scores, Double.NEGATIVE_INFINITY);
  }

}
