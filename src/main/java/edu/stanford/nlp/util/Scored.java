package edu.stanford.nlp.util;

/**
 * Scored: This is a simple interface that says that an object can answer
 * requests for the score, or goodness of the object.
 * <p>
 * JavaNLP includes companion classes {@link ScoredObject} which is a simple
 * composite of another object and a score, and {@link ScoredComparator}
 * which compares Scored objects.
 *
 * @author Dan Klein
 * @version 12/4/2000
 */
public interface Scored {

  /**
   * @return The score of this thing.
   */
  public double score();

}
