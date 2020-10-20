/**
 * <p>This contains predicate tests used in <code>SsurgeonPattern</code> objects.  The predicates are used to test nodes and edges, in order to add customized testing behaviors before executing the Ssurgeon pattern.</p>
 * <p>All tests must implement <code>SsurgPred</code> (Ssurgeon Predicate).</p>
 * <p>Tests are also implemented here, which accept nodes/edges as arguments.  The intent is to offer the ability to generate programmatic tests beyond those that are available in Semgrex, and to push these into Java code instead of making the Semgrex unreadable.</p>
 * <p>There are two tests, nodeTest and edgeTest, which accept the named node or edge from the match result.  These tests will return a boolean value.  The &quot;testID&quot; attribute of the tests indicates the named test to apply.</p>
 * <p>Currently, tests must be first registered with their String IDs with the given Ssurgeon rewriter.  This is a compromise, and we will address the ability to dyanmically specify tests at a future date.</p>
 */
package edu.stanford.nlp.semgraph.semgrex.ssurgeon.pred;