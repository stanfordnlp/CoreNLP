package edu.stanford.nlp.util;


/**
 * Holds a scored pair of objects.
 *
 * @author Dan Klein
 * @version 2/7/01
 */
public class ScoredPair<T1 extends Comparable<T1>,T2 extends Comparable<T2>> extends Pair<T1,T2> implements Scored {

  public double score;

  public double score() {
    return score;
  }

  public void setScore(double score) {
    this.score = score;
  }

  public ScoredPair() {
    first = null;
    second = null;
    score = 0;
  }

  public ScoredPair(T1 first, T2 second, double score) {
    this.first = first;
    this.second = second;
    this.score = score;
  }

  @Override
  public String toString() {
    return "(" + first + "," + second + ") %% " + score;
  }

  private static final long serialVersionUID = 1472506164021859706L;

}
