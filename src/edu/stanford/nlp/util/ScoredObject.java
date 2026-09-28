package edu.stanford.nlp.util;


import java.io.Serializable;

/**
 * Wrapper class for holding a scored object.
 *
 * @param <T> The type of the object
 * @author Dan Klein
 * @version 2/7/01
 */
public class ScoredObject<T> implements Scored, Serializable {

  /** The score of the object. */
  private double score;

  @Override
  public double score() {
    return score;
  }

  /**
   * Sets the score.
   *
   * @param score The new score
   */
  public void setScore(double score) {
    this.score = score;
  }


  /** The object being scored. */
  private T object;

  /**
   * Returns the object being scored.
   *
   * @return The object
   */
  public T object() {
    return object;
  }

  /**
   * Sets the object being scored.
   *
   * @param object The new object
   */
  public void setObject(T object) {
    this.object = object;
  }

  /**
   * Creates a ScoredObject holding the given object and score.
   *
   * @param object The object
   * @param score The score of the object
   */
  public ScoredObject(T object, double score) {
    this.object = object;
    this.score = score;
  }

  @Override
  public String toString() {
    return object + " @ " + score;
  }

  private static final long serialVersionUID = 1L;
}

