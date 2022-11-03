package edu.stanford.nlp.semgraph.semgrex.ssurgeon.pred;

import java.io.*;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;

public abstract class NodeTest implements SsurgPred {
  private String matchName = null; // This is the named node match in the Semgrex matcher, used to identify node to apply test on

  public abstract String getID();
  public abstract String getDisplayName();

  public NodeTest() { ; }

  public NodeTest(String matchName) { this.matchName = matchName; }

  public boolean test(SemgrexMatcher matcher) { return evaluate(matcher.getNode(matchName)); }

  // This is the custom routine to implement
  protected abstract boolean evaluate(IndexedWord node);

  // Use this for debugging, and dual re-use of the code outside of Ssurgeon
  public boolean test(IndexedWord node) {
    return evaluate(node);
  }

  @Override
  public String toString() {
    StringWriter buf = new StringWriter();
    buf.write("(node-test :name ");
    buf.write(getDisplayName());
    buf.write(" :id ");
    buf.write(getID());
    buf.write(" :match-name ");
    buf.write(matchName);
    buf.write(")");
    return buf.toString();
  }

  public String getMatchName() {
    return matchName;
  }
}
