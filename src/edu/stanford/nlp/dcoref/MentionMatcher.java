package edu.stanford.nlp.dcoref;

/**
 * Are two mentions compatible
 *
 * @author Angel Chang
 */
public interface MentionMatcher {

  /**
   * Determines if two mentions are compatible
   * @param m1 First mention to compare
   * @param m2 Second mention to compare
   * @return true if compatible, false if incompatible, null if not sure
   */
  public Boolean isCompatible(Mention m1, Mention m2);
}
