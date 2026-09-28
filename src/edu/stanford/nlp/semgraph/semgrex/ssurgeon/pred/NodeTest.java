package edu.stanford.nlp.semgraph.semgrex.ssurgeon.pred;

import java.io.*;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;

/**
 * A predicate which tests one named node of a Semgrex match.
 */
public abstract class NodeTest implements SsurgPred {
  private String matchName = null; // This is the named node match in the Semgrex matcher, used to identify node to apply test on

  /**
   * Returns the ID this kind of test is registered under in {@link SsurgTestManager}.
   *
   * @return The test's ID
   */
  public abstract String getID();
  /**
   * Returns a description of this test, used in {@link #toString()}.
   *
   * @return The display name
   */
  public abstract String getDisplayName();

  /** Creates a test with no node name. */
  public NodeTest() { ; }

  /**
   * Creates a test of the given named node.
   *
   * @param matchName The name of the node in the Semgrex pattern
   */
  public NodeTest(String matchName) { this.matchName = matchName; }

  public boolean test(SemgrexMatcher matcher) { return evaluate(matcher.getNode(matchName)); }

  // This is the custom routine to implement
  /**
   * Tests a node.
   *
   * @param node The node to test; null if the named node was not in the match
   * @return Whether the node passes the test
   */
  protected abstract boolean evaluate(IndexedWord node);

  // Use this for debugging, and dual re-use of the code outside of Ssurgeon
  /**
   * Tests a node directly, without a matcher.
   *
   * @param node The node to test
   * @return The result of the test
   */
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

  /**
   * Returns the name of the node this test is applied to.
   *
   * @return The node name
   */
  public String getMatchName() {
    return matchName;
  }
}
