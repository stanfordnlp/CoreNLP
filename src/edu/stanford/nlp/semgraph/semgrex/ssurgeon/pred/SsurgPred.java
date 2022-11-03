package edu.stanford.nlp.semgraph.semgrex.ssurgeon.pred;

import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;

public interface SsurgPred {
  // Given the current setup (each of the args in place), what is the truth value?  
  public boolean test(SemgrexMatcher matched);
}
