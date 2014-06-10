package edu.stanford.nlp.util;


/**
 * Wrapper class for holding a scored object
 *
 * @author Dan Klein
 * @version 2/7/01
 */
public class ScoredObject<T> implements Scored {

  private double score;

  public double score() {
    return score;
  }

  public void setScore(double score) {
    this.score = score;
  }


  private T object;

  public T object() {
    return object;
  }

  public void setObject(T object) {
    this.object = object;
  }

  public ScoredObject() {
  }

  public ScoredObject(T object, double score) {
    this.object = object;
    this.score = score;
  }

  @Override
  public String toString() {
    return object + " @ " + score;
  }

}
