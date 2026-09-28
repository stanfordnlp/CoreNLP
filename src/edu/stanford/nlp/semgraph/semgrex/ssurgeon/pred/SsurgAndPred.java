package edu.stanford.nlp.semgraph.semgrex.ssurgeon.pred;

import java.util.*;
import java.io.*;

import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;

/**
 * A predicate which is true when all of the predicates in this list are true,
 * including when the list is empty.
 */
public class SsurgAndPred extends ArrayList<SsurgPred> implements SsurgPred {

 
  /**
   * 
   */
  private static final long serialVersionUID = 760573332472162149L;

  /** Creates an empty conjunction. */
  public SsurgAndPred() { }

  public boolean test(SemgrexMatcher matcher) {
    for (SsurgPred term : this) {
      if (term.test(matcher) == false)
        return false;
    }
    return true;
  }

  @Override
  public String toString() {
    StringWriter buf = new StringWriter();
    buf.write("(ssurg-and");
    for (SsurgPred term: this) {
      buf.write(" ");
      buf.write(term.toString());
    }
    buf.write(")");
    return buf.toString();
  }

}
