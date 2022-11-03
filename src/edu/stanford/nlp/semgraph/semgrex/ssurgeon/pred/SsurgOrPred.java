package edu.stanford.nlp.semgraph.semgrex.ssurgeon.pred;

import java.util.*;
import java.io.*;

import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;

public class SsurgOrPred extends ArrayList<SsurgPred> implements SsurgPred {

  /**
   * 
   */
  private static final long serialVersionUID = 4581463857927967518L;

  public boolean test(SemgrexMatcher matcher) {
    for (SsurgPred term : this) {
      if (term.test(matcher))
        return true;
    }
    return false;
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
