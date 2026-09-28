package edu.stanford.nlp.semgraph.semgrex.ssurgeon.pred;

import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;

/**
 * A predicate which must be true for an SsurgeonPattern to apply its edits to a match.
 */
public interface SsurgPred {
  /**
   * Given the current setup (each of the args in place), what is the truth value?
   *
   * @param matched The matcher, positioned at the current match
   * @return Whether the predicate holds for this match
   */
  public boolean test(SemgrexMatcher matched);
}
