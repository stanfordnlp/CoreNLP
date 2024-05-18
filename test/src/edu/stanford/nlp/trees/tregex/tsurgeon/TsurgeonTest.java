package edu.stanford.nlp.trees.tregex.tsurgeon;

import junit.framework.TestCase;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.*;
import edu.stanford.nlp.util.Pair;

/**
 * Tests a few random tsurgeon operations.
 * TODO: needs more coverage.
 *
 * @author John Bauer
 * @author Christopher Manning
 */
public class TsurgeonTest extends TestCase {

  // We don't use valueOf because we sometimes use trees such as
  // (bar (foo (foo 1))), and the default valueOf uses a
  // TreeNormalizer that removes nodes from such a tree
  public static Tree treeFromString(String s) {
    try {
      TreeReader tr = new PennTreeReader(new StringReader(s),
                                         new LabeledScoredTreeFactory());
      return tr.readTree();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /** This was buggy in 2009 since the label started pointing to the node with ~n on it. */
  public void testBackReference() {
    TregexPattern tregex = TregexPattern.compile("__ <1 B=n <2 ~n");
    TsurgeonPattern tsurgeon = Tsurgeon.parseOperation("relabel n X");
    runTest(tregex, tsurgeon, "(A (B w) (B w))",
            "(A (X w) (B w))");
  }

  public void testForeign() {
    TregexPattern tregex = TregexPattern.compile("atentát=test");
    TsurgeonPattern tsurgeon = Tsurgeon.parseOperation("relabel test perform_atentát");
    runTest(tregex, tsurgeon, "(foo atentát)", "(foo perform_atentát)");
  }

  public void testAdjoin() {
    TsurgeonPattern tsurgeon = Tsurgeon.parseOperation("adjoin (FOO (BAR@)) foo");
    TregexPattern tregex = TregexPattern.compile("B=foo");
    runTest(tregex, tsurgeon, "(A (B 1 2))", "(A (FOO (BAR 1 2)))");
    runTest(tregex, tsurgeon, "(A (C 1 2))", "(A (C 1 2))");
    runTest(tregex, tsurgeon, "(A (B (B 1 2)))", "(A (FOO (BAR (FOO (BAR 1 2)))))");

    Tree tree = treeFromString("(A (B 1 2))");
    TregexMatcher matcher = tregex.matcher(tree);
    assertTrue(matcher.find());
    assertEquals("(B 1 2)", matcher.getNode("foo").toString());
    Tree updated = tsurgeon.matcher().evaluate(tree, matcher);
    assertEquals("(A (FOO (BAR 1 2)))", updated.toString());
    // TODO: do we want the tsurgeon to implicitly update the matched node?
    // System.err.println(matcher.getNode("foo"));
    assertFalse(matcher.find());
  }

  public void testAdjoinH() {
    TsurgeonPattern tsurgeon = Tsurgeon.parseOperation("adjoinH (FOO (BAR@)) foo");
    TregexPattern tregex = TregexPattern.compile("B=foo !< BAR");
    runTest(tregex, tsurgeon, "(A (B 1 2))", "(A (B (BAR 1 2)))");
    runTest(tregex, tsurgeon, "(A (C 1 2))", "(A (C 1 2))");
    runTest(tregex, tsurgeon, "(A (B (B 1 2)))", "(A (B (BAR (B (BAR 1 2)))))");

    Tree tree = treeFromString("(A (B 1 2))");
    TregexMatcher matcher = tregex.matcher(tree);
    assertTrue(matcher.find());
    assertEquals("(B 1 2)", matcher.getNode("foo").toString());
    Tree updated = tsurgeon.matcher().evaluate(tree, matcher);
    assertEquals("(A (B (BAR 1 2)))", updated.toString());
    assertEquals("(B (BAR 1 2))", matcher.getNode("foo").toString());
    assertFalse(matcher.find());
  }


  public void testAdjoinF() {
    TsurgeonPattern tsurgeon = Tsurgeon.parseOperation("adjoinF (FOO (BAR@)) foo");
    TregexPattern tregex = TregexPattern.compile("B=foo !> FOO");
    runTest(tregex, tsurgeon, "(A (B 1 2))", "(A (FOO (B 1 2)))");
    runTest(tregex, tsurgeon, "(A (C 1 2))", "(A (C 1 2))");
    runTest(tregex, tsurgeon, "(A (B (B 1 2)))", "(A (FOO (B (FOO (B 1 2)))))");

    Tree tree = treeFromString("(A (B 1 2))");
    TregexMatcher matcher = tregex.matcher(tree);
    assertTrue(matcher.find());
    assertEquals("(B 1 2)", matcher.getNode("foo").toString());
    Tree updated = tsurgeon.matcher().evaluate(tree, matcher);
    assertEquals("(A (FOO (B 1 2)))", updated.toString());
    assertEquals("(B 1 2)", matcher.getNode("foo").toString());
    assertFalse(matcher.find());
  }

  public void testAdjoinWithNamedNode() {
    TsurgeonPattern tsurgeon =
      Tsurgeon.parseOperation("[adjoinF (D (E=target foot@)) bar] " +
                              "[insert (G 1) $+ target]");
    TregexPattern tregex = TregexPattern.compile("B=bar !>> D");
    runTest(tregex, tsurgeon, "(A (B C))", "(A (D (G 1) (E (B C))))");

    tsurgeon =
      Tsurgeon.parseOperation("[adjoinF (D (E=target foot@)) bar] " +
                              "[insert (G 1) >0 target]");
    tregex = TregexPattern.compile("B=bar !>> D");
    runTest(tregex, tsurgeon, "(A (B C))", "(A (D (E (G 1) (B C))))");

    // Named leaf
    tsurgeon =
      Tsurgeon.parseOperation("[adjoinF (D (E foot@) F=target) bar] " +
                              "[insert (G 1) >0 target]");
    tregex = TregexPattern.compile("B=bar !>> D");
    runTest(tregex, tsurgeon, "(A (B C))", "(A (D (E (B C)) (F (G 1))))");
  }

  public void testAuxiliaryTreeErrors() {
    TsurgeonPattern tsurgeon;
    try {
      tsurgeon = Tsurgeon.parseOperation("adjoin (FOO (BAR)) foo");
      throw new RuntimeException("Should have failed for not having a foot");
    } catch (TsurgeonParseException e) {
      // yay
    }

    try {
      tsurgeon = Tsurgeon.parseOperation("adjoin (FOO (BAR@) (BAZ@)) foo");
      throw new RuntimeException("Should have failed for having two feet");
    } catch (TsurgeonParseException e) {
      // yay
    }

    try {
      tsurgeon = Tsurgeon.parseOperation("adjoin (FOO@ (BAR)) foo");
      throw new RuntimeException("Non-leaves cannot be foot nodes");
    } catch (TsurgeonParseException e) {
      // yay
    }
  }

  public void testCreateSubtrees() {
    TsurgeonPattern tsurgeon = Tsurgeon.parseOperation("createSubtree FOO left right");

    TregexPattern tregex = TregexPattern.compile("A < B=left < C=right");
    // Verify when there are only two nodes
    runTest(tregex, tsurgeon, "(A (B 1) (C 2))", "(A (FOO (B 1) (C 2)))");
    // We allow backwards nodes as well
    runTest(tregex, tsurgeon, "(A (C 1) (B 2))", "(A (FOO (C 1) (B 2)))");
    // Check nodes in between
    runTest(tregex, tsurgeon, "(A (B 1) (D 3) (C 2))", "(A (FOO (B 1) (D 3) (C 2)))");
    // Check nodes outside the span
    runTest(tregex, tsurgeon, "(A (D 3) (B 1) (C 2))", "(A (D 3) (FOO (B 1) (C 2)))");
    runTest(tregex, tsurgeon, "(A (B 1) (C 2) (D 3))", "(A (FOO (B 1) (C 2)) (D 3))");
    runTest(tregex, tsurgeon, "(A (D 3) (B 1) (C 2) (E 4))", "(A (D 3) (FOO (B 1) (C 2)) (E 4))");

    // Check when the two endpoints are the same
    tregex = TregexPattern.compile("A < B=left < B=right");
    runTest(tregex, tsurgeon, "(A (B 1) (C 2))", "(A (FOO (B 1)) (C 2))");

    // Check double operation - should make two FOO nodes and then stop
    runTest(tregex, tsurgeon, "(A (B 1) (B 2))", "(A (FOO (B 1)) (FOO (B 2)))");

    // Check when we only have one argument to createSubtree
    tsurgeon = Tsurgeon.parseOperation("createSubtree FOO child");
    tregex = TregexPattern.compile("A < B=child");
    runTest(tregex, tsurgeon, "(A (B 1) (C 2))", "(A (FOO (B 1)) (C 2))");
    runTest(tregex, tsurgeon, "(A (B 1) (B 2))", "(A (FOO (B 1)) (FOO (B 2)))");

    // Check that incorrectly formatted operations don't successfully parse
    try {
      tsurgeon = Tsurgeon.parseOperation("createSubtree FOO");
      throw new AssertionError("Expected to fail parsing");
    } catch (TsurgeonParseException e) {
      // yay
    }

    try {
      tsurgeon = Tsurgeon.parseOperation("createSubtree FOO a b c");
      throw new AssertionError("Expected to fail parsing");
    } catch (TsurgeonParseException e) {
      // yay
    }

    // Verify that it fails when the parents are different
    tsurgeon = Tsurgeon.parseOperation("createSubtree FOO left right");
    tregex = TregexPattern.compile("A << B=left << C=right");
    try {
      runTest(tregex, tsurgeon, "(A (B 1) (D (C 2)))", "(A (B 1) (D (C 2)))");
      throw new AssertionError("Expected a runtime failure");
    } catch (TsurgeonRuntimeException e) {
      // yay
    }
  }

  // Extended syntax for createSubtree: support arbitrary tree literals
  public void testCreateSubtreesExtended() {
    TsurgeonPattern tsurgeon = Tsurgeon.parseOperation("createSubtree (F (G 1) H@ I) left right");

    TregexPattern tregex = TregexPattern.compile("A < B=left < C=right");
    // Verify when there are only two nodes
    runTest(tregex, tsurgeon, "(A (B 1) (C 2))", "(A (F (G 1) (H (B 1) (C 2)) I))");
    // We allow backwards nodes as well
    runTest(tregex, tsurgeon, "(A (C 1) (B 2))", "(A (F (G 1) (H (C 1) (B 2)) I))");
    // Check nodes in between
    runTest(tregex, tsurgeon, "(A (B 1) (D 3) (C 2))", "(A (F (G 1) (H (B 1) (D 3) (C 2)) I))");
    // Check nodes outside the span
    runTest(tregex, tsurgeon, "(A (D 3) (B 1) (C 2))", "(A (D 3) (F (G 1) (H (B 1) (C 2)) I))");
    runTest(tregex, tsurgeon, "(A (B 1) (C 2) (D 3))", "(A (F (G 1) (H (B 1) (C 2)) I) (D 3))");
    runTest(tregex, tsurgeon, "(A (D 3) (B 1) (C 2) (E 4))", "(A (D 3) (F (G 1) (H (B 1) (C 2)) I) (E 4))");

    // Check when the two endpoints are the same
    tregex = TregexPattern.compile("A < B=left < B=right");
    runTest(tregex, tsurgeon, "(A (B 1) (C 2))", "(A (F (G 1) (H (B 1)) I) (C 2))");

    // Check double operation - should make two F nodes and then stop
    runTest(tregex, tsurgeon, "(A (B 1) (B 2))", "(A (F (G 1) (H (B 1)) I) (F (G 1) (H (B 2)) I))");

    // Check when we only have one argument to createSubtree
    tsurgeon = Tsurgeon.parseOperation("createSubtree (F (G 1) H@ I) child");
    tregex = TregexPattern.compile("A < B=child");
    runTest(tregex, tsurgeon, "(A (B 1) (C 2))", "(A (F (G 1) (H (B 1)) I) (C 2))");
    runTest(tregex, tsurgeon, "(A (B 1) (B 2))", "(A (F (G 1) (H (B 1)) I) (F (G 1) (H (B 2)) I))");

    // Check that incorrectly formatted operations don't successfully parse
    try {
      tsurgeon = Tsurgeon.parseOperation("createSubtree (F (G 1) H@ I)");
      throw new AssertionError("Expected to fail parsing");
    } catch (TsurgeonParseException e) {
      // yay
    }

    try {
      tsurgeon = Tsurgeon.parseOperation("createSubtree (F (G 1) H@ I) a b c");
      throw new AssertionError("Expected to fail parsing");
    } catch (TsurgeonParseException e) {
      // yay
    }

    // Missing foot
    try {
      tsurgeon = Tsurgeon.parseOperation("createSubtree (F (G 1) H I) a b c");
      throw new AssertionError("Expected to fail parsing");
    } catch (TsurgeonParseException e) {
      // yay
    }

    // Verify that it fails when the parents are different
    tsurgeon = Tsurgeon.parseOperation("createSubtree (F (G 1) H@ I) left right");
    tregex = TregexPattern.compile("A << B=left << C=right");
    try {
      runTest(tregex, tsurgeon, "(A (B 1) (D (C 2)))", "(A (B 1) (D (C 2)))");
      throw new AssertionError("Expected a runtime failure");
    } catch (TsurgeonRuntimeException e) {
      // yay
    }
  }

  public void testDelete() {
    TsurgeonPattern tsurgeon = Tsurgeon.parseOperation("delete bob");

    TregexPattern tregex = TregexPattern.compile("B=bob");
    runTest(tregex, tsurgeon, "(A (B (C 1)))", "A");
    runTest(tregex, tsurgeon, "(A (foo 1) (B (C 1)))", "(A (foo 1))");
    runTest(tregex, tsurgeon, "(A (B 1) (B (C 1)))", "A");
    runTest(tregex, tsurgeon, "(A (foo 1) (bar (C 1)))",
            "(A (foo 1) (bar (C 1)))");

    tregex = TregexPattern.compile("C=bob");
    runTest(tregex, tsurgeon, "(A (B (C 1)))", "(A B)");
    runTest(tregex, tsurgeon, "(A (foo 1) (B (C 1)))", "(A (foo 1) B)");
    runTest(tregex, tsurgeon, "(A (B 1) (B (C 1)))", "(A (B 1) B)");
    runTest(tregex, tsurgeon, "(A (foo 1) (bar (C 1)))", "(A (foo 1) bar)");
  }

  public void testPrune() {
    TsurgeonPattern tsurgeon = Tsurgeon.parseOperation("prune bob");

    TregexPattern tregex = TregexPattern.compile("B=bob");
    runTest(tregex, tsurgeon, "(A (B (C 1)))", null);
    runTest(tregex, tsurgeon, "(A (foo 1) (B (C 1)))", "(A (foo 1))");
    runTest(tregex, tsurgeon, "(A (B 1) (B (C 1)))", null);
    runTest(tregex, tsurgeon, "(A (foo 1) (bar (C 1)))",
            "(A (foo 1) (bar (C 1)))");

    tregex = TregexPattern.compile("C=bob");
    runTest(tregex, tsurgeon, "(A (B (C 1)))", null);
    runTest(tregex, tsurgeon, "(A (foo 1) (B (C 1)))", "(A (foo 1))");
    runTest(tregex, tsurgeon, "(A (B 1) (B (C 1)))", "(A (B 1))");
    runTest(tregex, tsurgeon, "(A (foo 1) (bar (C 1)))", "(A (foo 1))");
  }

  public void testInsert() {
    TsurgeonPattern tsurgeon =
      Tsurgeon.parseOperation("insert (D (E 6)) $+ bar");
    TregexPattern tregex = TregexPattern.compile("B=bar !$ D");
    runTest(tregex, tsurgeon, "(A (B 0) (C 1))", "(A (D (E 6)) (B 0) (C 1))");

    tsurgeon = Tsurgeon.parseOperation("insert (D (E 6)) $- bar");
    runTest(tregex, tsurgeon, "(A (B 0) (C 1))", "(A (B 0) (D (E 6)) (C 1))");

    tsurgeon = Tsurgeon.parseOperation("insert (D (E 6)) >0 bar");
    tregex = TregexPattern.compile("B=bar !<D");
    runTest(tregex, tsurgeon, "(A (B 0) (C 1))", "(A (B (D (E 6)) 0) (C 1))");

    tsurgeon = Tsurgeon.parseOperation("insert foo >0 bar");
    tregex = TregexPattern.compile("B=bar !<C $C=foo");
    runTest(tregex, tsurgeon, "(A (B 0) (C 1))", "(A (B (C 1) 0) (C 1))");

    // the name will be cut off
    tsurgeon = Tsurgeon.parseOperation("insert (D (E=blah 6)) >0 bar");
    tregex = TregexPattern.compile("B=bar !<D");
    runTest(tregex, tsurgeon, "(A (B 0) (C 1))", "(A (B (D (E 6)) 0) (C 1))");

    // the name should not be cut off, with the escaped = unescaped now
    tsurgeon = Tsurgeon.parseOperation("insert (D (E\\=blah 6)) >0 bar");
    tregex = TregexPattern.compile("B=bar !<D");
    runTest(tregex, tsurgeon, "(A (B 0) (C 1))", "(A (B (D (E=blah 6)) 0) (C 1))");

    // the name should be cut off again, with a \ at the end of the new node
    tsurgeon = Tsurgeon.parseOperation("insert (D (E\\\\=blah 6)) >0 bar");
    tregex = TregexPattern.compile("B=bar !<D");
    runTest(tregex, tsurgeon, "(A (B 0) (C 1))", "(A (B (D (E\\ 6)) 0) (C 1))");
  }

  public void testInsertWithNamedNode() {
    TsurgeonPattern tsurgeon = Tsurgeon.parseOperation("[insert (D=target E) $+ bar] " +
                                                       "[insert (F 1) >0 target]");
    TregexPattern tregex = TregexPattern.compile("B=bar !$- D");
    runTest(tregex, tsurgeon, "(A (B C))", "(A (D (F 1) E) (B C))");

    tsurgeon = Tsurgeon.parseOperation("[insert (D=target E) $+ bar] " +
                                       "[insert (F 1) $+ target]");
    tregex = TregexPattern.compile("B=bar !$- D");
    runTest(tregex, tsurgeon, "(A (B C))", "(A (F 1) (D E) (B C))");

    // Named leaf
    tsurgeon = Tsurgeon.parseOperation("[insert (D E=target) $+ bar] " +
                                       "[insert (F 1) $+ target]");
    tregex = TregexPattern.compile("B=bar !$- D");
    runTest(tregex, tsurgeon, "(A (B C))", "(A (D (F 1) E) (B C))");
  }

  public void testRelabel() {
    TsurgeonPattern tsurgeon;
    TregexPattern tregex;

    tregex = TregexPattern.compile("/^((?!_head).)*$/=preTerminal < (__=terminal !< __)");
    tsurgeon = Tsurgeon.parseOperation("relabel preTerminal /^(.*)$/$1_head=={terminal}/");
    runTest(tregex, tsurgeon, "($ $)", "($_head=$ $)");

    tsurgeon = Tsurgeon.parseOperation("relabel foo blah");
    tregex = TregexPattern.compile("B=foo");
    runTest(tregex, tsurgeon, "(A (B 0) (C 1))", "(A (blah 0) (C 1))");
    runTest(tregex, tsurgeon, "(A (B 0) (B 1))", "(A (blah 0) (blah 1))");

    // test a few simple expressions with unusual characters
    tsurgeon = Tsurgeon.parseOperation("relabel foo /\\//");
    tregex = TregexPattern.compile("B=foo");
    runTest(tregex, tsurgeon, "(A (B 0) (C 1))", "(A (/ 0) (C 1))");
    runTest(tregex, tsurgeon, "(A (B 0) (B 1))", "(A (/ 0) (/ 1))");

    tsurgeon = Tsurgeon.parseOperation("relabel foo /{/");
    tregex = TregexPattern.compile("B=foo");
    runTest(tregex, tsurgeon, "(A (B 0) (C 1))", "(A ({ 0) (C 1))");
    runTest(tregex, tsurgeon, "(A (B 0) (B 1))", "(A ({ 0) ({ 1))");

    tsurgeon = Tsurgeon.parseOperation("relabel foo /[/");
    tregex = TregexPattern.compile("B=foo");
    runTest(tregex, tsurgeon, "(A (B 0) (C 1))", "(A ([ 0) (C 1))");
    runTest(tregex, tsurgeon, "(A (B 0) (B 1))", "(A ([ 0) ([ 1))");

    tsurgeon = Tsurgeon.parseOperation("relabel foo /\\]/");
    tregex = TregexPattern.compile("B=foo");
    runTest(tregex, tsurgeon, "(A (B 0) (C 1))", "(A (] 0) (C 1))");
    runTest(tregex, tsurgeon, "(A (B 0) (B 1))", "(A (] 0) (] 1))");

    tsurgeon = Tsurgeon.parseOperation("relabel foo /.*(voc.*)/$1/");
    tregex = TregexPattern.compile("/^a.*t/=foo");
    runTest(tregex, tsurgeon, "(A (avocet 0) (C 1))", "(A (vocet 0) (C 1))");
    runTest(tregex, tsurgeon, "(A (avocet 0) (advocate 1))",
            "(A (vocet 0) (vocate 1))");

    tregex = TregexPattern.compile("curlew=baz < /^a(.*)t/#1%bar=foo");

    tsurgeon = Tsurgeon.parseOperation("relabel baz /cu(rle)w/={foo}/");
    runTest(tregex, tsurgeon, "(curlew (avocet 0))", "(avocet (avocet 0))");
    tsurgeon = Tsurgeon.parseOperation("relabel baz /cu(rle)w/%{bar}/");
    runTest(tregex, tsurgeon, "(curlew (avocet 0))", "(voce (avocet 0))");
    tsurgeon = Tsurgeon.parseOperation("relabel baz /cu(rle)w/$1/");
    runTest(tregex, tsurgeon, "(curlew (avocet 0))", "(rle (avocet 0))");
    tsurgeon = Tsurgeon.parseOperation("relabel baz /cu(rle)w/$1={foo}/");
    runTest(tregex, tsurgeon, "(curlew (avocet 0))", "(rleavocet (avocet 0))");
    tsurgeon = Tsurgeon.parseOperation("relabel baz /cu(rle)w/%{bar}$1={foo}/");
    runTest(tregex, tsurgeon,
            "(curlew (avocet 0))", "(vocerleavocet (avocet 0))");

    tregex = TregexPattern.compile("A=baz < /curlew.*/=foo < /avocet.*/=bar");
    tsurgeon = Tsurgeon.parseOperation("relabel baz /^.*$/={foo}={bar}/");
    runTest(tregex, tsurgeon, "(A (curlewfoo 0) (avocetzzz 1))",
            "(curlewfooavocetzzz (curlewfoo 0) (avocetzzz 1))");

    tregex = TregexPattern.compile("A=baz < /curle.*/=foo < /avo(.*)/#1%bar");
    tsurgeon = Tsurgeon.parseOperation("relabel baz /^(.*)$/={foo}$1%{bar}/");
    runTest(tregex, tsurgeon, "(A (curlew 0) (avocet 1))",
            "(curlewAcet (curlew 0) (avocet 1))");

    tsurgeon = Tsurgeon.parseOperation("relabel baz /^(.*)$/=foo$1%bar/");
    runTest(tregex, tsurgeon, "(A (curlew 0) (avocet 1))",
            "(=fooA%bar (curlew 0) (avocet 1))");

    tregex = TregexPattern.compile("/foo/=foo");
    tsurgeon = Tsurgeon.parseOperation("relabel foo /foo/bar/");
    runTest(tregex, tsurgeon, "(foofoo (curlew 0) (avocet 1))",
            "(barbar (curlew 0) (avocet 1))");

    tregex = TregexPattern.compile("/foo/=foo < /cur.*/=bar");
    tsurgeon = Tsurgeon.parseOperation("relabel foo /foo/={bar}/");
    runTest(tregex, tsurgeon, "(foofoo (curlew 0) (avocet 1))",
            "(curlewcurlew (curlew 0) (avocet 1))");

    tregex = TregexPattern.compile("/^foo(.*)$/=foo");
    tsurgeon = Tsurgeon.parseOperation("relabel foo /foo(.*)$/bar$1/");
    runTest(tregex, tsurgeon, "(foofoo (curlew 0) (avocet 1))",
            "(barfoo (curlew 0) (avocet 1))");
  }

  /**
   * Test relabeling a tree from icepahc
   *
   * The goal is to check that removing the lemmas and combining detached words both work as expected
   */
  public void testRelabelICE() {
    String treeText = "( (IP-MAT (NP-SBJ (PRO-N Það-það)) (BEPI er-vera) (ADVP (ADV eiginlega-eiginlega)) (ADJP (NEG ekki-ekki) (ADJ-N hægt-hægur)) (IP-INF (TO að-að) (VB lýsa-lýsa)) (NP-OB1 (N-D tilfinningu$-tilfinning) (D-D $nni-hinn)) (IP-INF (TO að-að) (VB fá-fá)) (IP-INF (TO að-að) (VB taka-taka)) (NP-OB1 (N-A þátt-þáttur)) (PP (P í-í) (NP (D-D þessu-þessi))) (, ,-,) (VBPI segir-segja) (NP-SBJ (NPR-N Sverrir-sverrir) (NPR-N Ingi-ingi)) (. .-.)))";

    String relabeledTreeText = "( (IP-MAT (NP-SBJ (PRO-N Það)) (BEPI er) (ADVP (ADV eiginlega)) (ADJP (NEG ekki) (ADJ-N hægt)) (IP-INF (TO að) (VB lýsa)) (NP-OB1 (N-D tilfinningu$) (D-D $nni)) (IP-INF (TO að) (VB fá)) (IP-INF (TO að) (VB taka)) (NP-OB1 (N-A þátt)) (PP (P í) (NP (D-D þessu))) (, ,) (VBPI segir) (NP-SBJ (NPR-N Sverrir) (NPR-N Ingi)) (. .)))";

    TregexPattern tregex = TregexPattern.compile("/^(.+)-.+$/#1%form=word !< __");
    TsurgeonPattern tsurgeon = Tsurgeon.parseOperation("relabel word /^(.+)-.+$/%{form}/");
    runTest(tregex, tsurgeon, treeText, relabeledTreeText);

    tregex = TregexPattern.compile("/^N-/ < /^([^$]+)[$]$/#1%noun=noun $+ (/^D-/ < /^[$]([^$]+)$/#1%det=det)");
    tsurgeon = Tsurgeon.parseOperation("relabel noun /^.+$/%{noun}%{det}/");
    runTest(tregex, tsurgeon, relabeledTreeText,
            "( (IP-MAT (NP-SBJ (PRO-N Það)) (BEPI er) (ADVP (ADV eiginlega)) (ADJP (NEG ekki) (ADJ-N hægt)) (IP-INF (TO að) (VB lýsa)) (NP-OB1 (N-D tilfinningunni) (D-D $nni)) (IP-INF (TO að) (VB fá)) (IP-INF (TO að) (VB taka)) (NP-OB1 (N-A þátt)) (PP (P í) (NP (D-D þessu))) (, ,) (VBPI segir) (NP-SBJ (NPR-N Sverrir) (NPR-N Ingi)) (. .)))");
  }

  public void testReplaceNode() {
    TsurgeonPattern tsurgeon = Tsurgeon.parseOperation("replace foo blah");
    TregexPattern tregex = TregexPattern.compile("B=foo : C=blah");
    runTest(tregex, tsurgeon, "(A (B 0) (C 1))", "(A (C 1) (C 1))");

    // This test was a bug reported by a user; only one of the -NONE-
    // nodes was being replaced.  This was because the replace was
    // reusing existing tree nodes instead of creating new ones, which
    // caused tregex to fail to find the second replacement
    tsurgeon = Tsurgeon.parseOperation("replace dest src");
    tregex = TregexPattern.compile("(/-([0-9]+)$/#1%i=src > /^FILLER$/) : (/^-NONE-/=dest <: /-([0-9]+)$/#1%i)");
    runTest(tregex, tsurgeon,
            "( (S (FILLER (NP-SBJ-1 (NNP Koito))) (VP (VBZ has) (VP (VBN refused) (S (NP-SBJ (-NONE- *-1)) (VP (TO to) (VP (VB grant) (NP (NNP Mr.) (NNP Pickens)) (NP (NP (NNS seats)) (PP-LOC (IN on) (NP (PRP$ its) (NN board))))))) (, ,) (S-ADV (NP-SBJ (-NONE- *-1)) (VP (VBG asserting) (SBAR (-NONE- 0) (S (NP-SBJ (PRP he)) (VP (VBZ is) (NP-PRD (NP (DT a) (NN greenmailer)) (VP (VBG trying) (S (NP-SBJ (-NONE- *)) (VP (TO to) (VP (VB pressure) (NP (NP (NNP Koito) (POS 's)) (JJ other) (NNS shareholders)) (PP-CLR (IN into) (S-NOM (NP-SBJ (-NONE- *)) (VP (VBG buying) (NP (PRP him)) (PRT (RP out)) (PP-MNR (IN at) (NP (DT a) (NN profit)))))))))))))))))) (. .)))",
            "( (S (FILLER (NP-SBJ-1 (NNP Koito))) (VP (VBZ has) (VP (VBN refused) (S (NP-SBJ (NP-SBJ-1 (NNP Koito))) (VP (TO to) (VP (VB grant) (NP (NNP Mr.) (NNP Pickens)) (NP (NP (NNS seats)) (PP-LOC (IN on) (NP (PRP$ its) (NN board))))))) (, ,) (S-ADV (NP-SBJ (NP-SBJ-1 (NNP Koito))) (VP (VBG asserting) (SBAR (-NONE- 0) (S (NP-SBJ (PRP he)) (VP (VBZ is) (NP-PRD (NP (DT a) (NN greenmailer)) (VP (VBG trying) (S (NP-SBJ (-NONE- *)) (VP (TO to) (VP (VB pressure) (NP (NP (NNP Koito) (POS 's)) (JJ other) (NNS shareholders)) (PP-CLR (IN into) (S-NOM (NP-SBJ (-NONE- *)) (VP (VBG buying) (NP (PRP him)) (PRT (RP out)) (PP-MNR (IN at) (NP (DT a) (NN profit)))))))))))))))))) (. .)))");
  }

  public void testReplaceTree() {
    TsurgeonPattern tsurgeon = Tsurgeon.parseOperation("replace foo (BAR 1)");
    TregexPattern tregex = TregexPattern.compile("B=foo");
    runTest(tregex, tsurgeon, "(A (B 0) (B 1) (C 2))", "(A (BAR 1) (BAR 1) (C 2))");

    // test that a single replacement at the root is allowed
    runTest(tregex, tsurgeon, "(B (C 1))", "(BAR 1)");

    tsurgeon = Tsurgeon.parseOperation("replace foo (BAR 1) (BAZ 2)");
    runTest(tregex, tsurgeon, "(A (B 0) (B 1) (C 2))", "(A (BAR 1) (BAZ 2) (BAR 1) (BAZ 2) (C 2))");

    try {
      runTest(tregex, tsurgeon, "(B 0)", "(B 0)");
      throw new RuntimeException("Expected a failure");
    } catch (TsurgeonRuntimeException e) {
      // good, we expected to fail if you try to replace the root node with two nodes
    }

    // it is possible for numbers to work and words to not work if
    // the tsurgeon parser is not correct
    tsurgeon = Tsurgeon.parseOperation("replace foo (BAR blah)");
    tregex = TregexPattern.compile("B=foo");
    runTest(tregex, tsurgeon, "(A (B 0) (B 1) (C 2))", "(A (BAR blah) (BAR blah) (C 2))");
  }

  // public void testKeywords() {
  //   TsurgeonPattern tsurgeon = Tsurgeon.parseOperation("replace foo replace");
  // }

  /**
   * Test (part of) an actual tree that we use in the Chinese transforming reader
   */
  public void testChineseReplaceTree() {
    String input = "(IP (IP (PP (P 像) (NP (NP (NR 赖斯) (PU ，) (NR 赖斯)) (NP (PN 本身)))) (PU 她｛) (NP (NN ｂｒｅａｔｈ)) (PU ｝) (IJ 呃) (VP (VV 担任) (NP (NN 国务卿)) (VP (ADVP (AD 比较)) (VP (VA 晚))))))";
    String expected = "(IP (IP (PP (P 像) (NP (NP (NR 赖斯) (PU ，) (NR 赖斯)) (NP (PN 本身)))) (PN 她) (PU ｛) (NP (NN ｂｒｅａｔｈ)) (PU ｝) (IJ 呃) (VP (VV 担任) (NP (NN 国务卿)) (VP (ADVP (AD 比较)) (VP (VA 晚))))))";
    TregexPattern tregex = TregexPattern.compile("PU=punc < 她｛");
    TsurgeonPattern tsurgeon = Tsurgeon.parseOperation("replace punc (PN 她) (PU ｛)");
    runTest(tregex, tsurgeon, input, expected);
  }

  public void testInsertDelete() {
    // The same bug as the Replace bug, but for a sequence of
    // insert/delete operations
    List<Pair<TregexPattern, TsurgeonPattern>> surgery = new ArrayList<>();

    TregexPattern tregex = TregexPattern.compile("(/-([0-9]+)$/#1%i=src > /^FILLER$/) : (/^-NONE-/=dest <: /-([0-9]+)$/#1%i !$ ~src)");
    TsurgeonPattern tsurgeon = Tsurgeon.parseOperation("insert src $+ dest");
    surgery.add(new Pair<TregexPattern, TsurgeonPattern>(tregex, tsurgeon));
    tregex = TregexPattern.compile("(/-([0-9]+)$/#1%i=src > /^FILLER$/) : (/^-NONE-/=dest <: /-([0-9]+)$/#1%i)");
    tsurgeon = Tsurgeon.parseOperation("delete dest");
    surgery.add(new Pair<TregexPattern, TsurgeonPattern>(tregex, tsurgeon));

    runTest(surgery,
            "( (S (FILLER (NP-SBJ-1 (NNP Koito))) (VP (VBZ has) (VP (VBN refused) (S (NP-SBJ (-NONE- *-1)) (VP (TO to) (VP (VB grant) (NP (NNP Mr.) (NNP Pickens)) (NP (NP (NNS seats)) (PP-LOC (IN on) (NP (PRP$ its) (NN board))))))) (, ,) (S-ADV (NP-SBJ (-NONE- *-1)) (VP (VBG asserting) (SBAR (-NONE- 0) (S (NP-SBJ (PRP he)) (VP (VBZ is) (NP-PRD (NP (DT a) (NN greenmailer)) (VP (VBG trying) (S (NP-SBJ (-NONE- *)) (VP (TO to) (VP (VB pressure) (NP (NP (NNP Koito) (POS 's)) (JJ other) (NNS shareholders)) (PP-CLR (IN into) (S-NOM (NP-SBJ (-NONE- *)) (VP (VBG buying) (NP (PRP him)) (PRT (RP out)) (PP-MNR (IN at) (NP (DT a) (NN profit)))))))))))))))))) (. .)))",
            "( (S (FILLER (NP-SBJ-1 (NNP Koito))) (VP (VBZ has) (VP (VBN refused) (S (NP-SBJ (NP-SBJ-1 (NNP Koito))) (VP (TO to) (VP (VB grant) (NP (NNP Mr.) (NNP Pickens)) (NP (NP (NNS seats)) (PP-LOC (IN on) (NP (PRP$ its) (NN board))))))) (, ,) (S-ADV (NP-SBJ (NP-SBJ-1 (NNP Koito))) (VP (VBG asserting) (SBAR (-NONE- 0) (S (NP-SBJ (PRP he)) (VP (VBZ is) (NP-PRD (NP (DT a) (NN greenmailer)) (VP (VBG trying) (S (NP-SBJ (-NONE- *)) (VP (TO to) (VP (VB pressure) (NP (NP (NNP Koito) (POS 's)) (JJ other) (NNS shareholders)) (PP-CLR (IN into) (S-NOM (NP-SBJ (-NONE- *)) (VP (VBG buying) (NP (PRP him)) (PRT (RP out)) (PP-MNR (IN at) (NP (DT a) (NN profit)))))))))))))))))) (. .)))");
  }

  /**
   * There was a bug where repeated children with the same exact
   * structure meant that each of the children would be repeated, even
   * if some of them wouldn't match the tree structure.  For example,
   * if you had the tree <code>(NP NP , NP , NP , CC NP)</code> and
   * tried to replace with <code>@NP &lt; (/^,/=comma $+ CC)</code>,
   * all of the commas would be replaced, not just the one next to CC.
   */
  public void testReplaceWithRepeats() {
    TsurgeonPattern tsurgeon;
    TregexPattern tregex;

    tregex = TregexPattern.compile("@NP < (/^,/=comma $+ CC)");
    tsurgeon = Tsurgeon.parseOperation("replace comma (COMMA)");
    runTest(tregex, tsurgeon, "(NP NP , NP , NP , CC NP)", "(NP NP , NP , NP COMMA CC NP)");
  }

  public void testCoindex() {
    TregexPattern tregex = TregexPattern.compile("A=foo << B=bar << C=baz");
    TsurgeonPattern tsurgeon = Tsurgeon.parseOperation("coindex foo bar baz");
    runTest(tregex, tsurgeon, "(A (B (C foo)))", "(A-1 (B-1 (C-1 foo)))");
    // note that the indexing does not happen a second time, since the labels are now changed
    runTest(tregex, tsurgeon, "(A (B foo) (C foo) (C bar))", "(A-1 (B-1 foo) (C-1 foo) (C bar))");

    // Test that it indexes at 2 instead of 1
    runTest(tregex, tsurgeon, "(A (B foo) (C-1 bar) (C baz))", "(A-2 (B-2 foo) (C-1 bar) (C-2 baz))");
  }

  /**
   * Since tsurgeon uses a lot of keywords, those keywords would not
   * be allowed in the operations unless you handle them correctly
   * (for example, using lexical states).  This tests that this is
   * done correctly.
   */
  public void testKeyword() {
    // This should successfully compile, assuming the keyword parsing is correct
    TregexPattern tregex = TregexPattern.compile("A=foo << B=bar << C=baz");
    TsurgeonPattern tsurgeon = Tsurgeon.parseOperation("relabel foo relabel");
    runTest(tregex, tsurgeon, "(A (B foo) (C foo) (C bar))", "(relabel (B foo) (C foo) (C bar))");
  }

  /**
   * You can compile multiple patterns into one node with the syntax
   * [pattern1] [pattern2]
   * Test that it does what it is supposed to do
   */
  public void testMultiplePatterns() {
    TregexPattern tregex = TregexPattern.compile("A=foo < B=bar < C=baz");
    TsurgeonPattern tsurgeon = Tsurgeon.parseOperation("[relabel baz BAZ] [move baz >-1 bar]");
    runTest(tregex, tsurgeon, "(A (B foo) (C foo) (C bar))", "(A (B foo (BAZ foo) (BAZ bar)))");

    tsurgeon = Tsurgeon.parseOperation("[relabel baz /^.*$/={bar}={baz}FOO/] [move baz >-1 bar]");
    runTest(tregex, tsurgeon, "(A (B foo) (C foo) (C bar))", "(A (B foo (BCFOO foo) (BCFOO bar)))");

    // This in particular was a problem until we required "/" to be escaped
    tregex = TregexPattern.compile("A=foo < B=bar < C=baz < D=biff");
    tsurgeon = Tsurgeon.parseOperation("[relabel baz /^.*$/={bar}={baz}/] [relabel biff /^.*$/={bar}={biff}/]");
    runTest(tregex, tsurgeon, "(A (B foo) (C bar) (D baz))", "(A (B foo) (BC bar) (BD baz))");
  }

  public void testIfExists() {
    // This should successfully compile, assuming the keyword parsing is correct
    TregexPattern tregex = TregexPattern.compile("A=foo [ << B=bar | << C=baz ]");
    TsurgeonPattern tsurgeon = Tsurgeon.parseOperation("if exists bar relabel bar BAR");
    runTest(tregex, tsurgeon, "(A (B foo))", "(A (BAR foo))");

    tsurgeon = Tsurgeon.parseOperation("[if exists bar relabel bar BAR] [if exists baz relabel baz BAZ]");
    runTest(tregex, tsurgeon, "(A (B foo))", "(A (BAR foo))");
    runTest(tregex, tsurgeon, "(A (C foo))", "(A (BAZ foo))");
    runTest(tregex, tsurgeon, "(A (B foo) (C foo))", "(A (BAR foo) (BAZ foo))");

    String tree = new String("(ROOT (INTJ (CC But) (S (NP (DT the) (NNP RTC)) (ADVP (RB also)) (VP (VBZ requires) (`` ``) (S (FRAG (VBG working) ('' '') (NP (NP (NN capital)) (S (VP (TO to) (VP (VB maintain) (SBAR (S (NP (NP (DT the) (JJ bad) (NNS assets)) (PP (IN of) (NP (NP (NNS thrifts)) (SBAR (WHNP (WDT that)) (S (VP (VBP are) (VBN sold) (, ,) (PP (IN until) (NP (DT the) (NNS assets))))))))) (VP (MD can) (VP (VB be) (VP (VBN sold) (ADVP (RB separately))))))))))))))) (S (VP (. .)))))");
    String expected = new String("(ROOT (INTJ (CC But) (S (NP (DT the) (NNP RTC)) (ADVP (RB also)) (VP (VBZ requires) (`` ``) (S (FRAG (VBG working) ('' '') (NP (NP (NN capital)) (S (VP (TO to) (VP (VB maintain) (SBAR (S (NP (NP (DT the) (JJ bad) (NNS assets)) (PP (IN of) (NP (NP (NNS thrifts)) (SBAR (WHNP (WDT that)) (S (VP (VBP are) (VBN sold) (, ,) (PP (IN until) (NP (DT the) (NNS assets))))))))) (VP (MD can) (VP (VB be) (VP (VBN sold) (ADVP (RB separately))))))))))))))) (. .)))");
    tregex = TregexPattern.compile("__ !> __ <- (__=top <- (__ <<- (/[.]|PU/=punc < /[.!?。！？]/ ?> (__=single <: =punc))))");
    tsurgeon = Tsurgeon.parseOperation("[move punc >-1 top] [if exists single prune single]");
    runTest(tregex, tsurgeon, tree, expected);
  }

  public void testMove() {
    TregexPattern tregex = TregexPattern.compile("__ !> __ <1 /``/=bad <2 S=good");
    TsurgeonPattern tsurgeon = Tsurgeon.parseOperation("[move bad >1 good]");
    runTest(tregex, tsurgeon, "(TOP (`` ``) (S foo))", "(TOP (S (`` ``) foo))");
  }

  /** 
   * Demonstrate why move/prune would be useful, then test that it does the thing
   */
  public void testMovePrune() {
    TregexPattern tregex = TregexPattern.compile("__ !> __ <1 (A < B=bad) <2 C=good");
    TsurgeonPattern tsurgeon = Tsurgeon.parseOperation("[move bad >1 good]");
    // the A is left by itself once the B is moved
    runTest(tregex, tsurgeon, "(ROOT (A (B b)) (C c))", "(ROOT A (C (B b) c))");
    // here it makes sense for the A to have a child still
    runTest(tregex, tsurgeon, "(ROOT (A (B b) (D d)) (C c))", "(ROOT (A (D d)) (C (B b) c))");

    tsurgeon = Tsurgeon.parseOperation("[moveprune bad >1 good]");
    // the "prune" version should get rid of A
    runTest(tregex, tsurgeon, "(ROOT (A (B b)) (C c))", "(ROOT (C (B b) c))");
    // in this case, A isn't empty, so it shouldn't be pruned
    runTest(tregex, tsurgeon, "(ROOT (A (B b) (D d)) (C c))", "(ROOT (A (D d)) (C (B b) c))");
  }

  public void testExcise() {
    // TODO: needs more meat to this test
    TregexPattern tregex = TregexPattern.compile("__=repeat <: (~repeat < __)");
    TsurgeonPattern tsurgeon = Tsurgeon.parseOperation("excise repeat repeat");
    runTest(tregex, tsurgeon, "(A (B (B foo)))", "(A (B foo))");
    // Test that if a deleted root is excised down to a level that has
    // just one child, that one child gets returned as the new tree
    runTest(tregex, tsurgeon, "(B (B foo))", "(B foo)");

    tregex = TregexPattern.compile("A=root");
    tsurgeon = Tsurgeon.parseOperation("excise root root");
    runTest(tregex, tsurgeon, "(A (B bar) (C foo))", null);
  }

  public static void runTest(TregexPattern tregex, TsurgeonPattern tsurgeon,
                      String input, String expected) {
    Tree result = Tsurgeon.processPattern(tregex, tsurgeon,
                                          treeFromString(input));
    if (expected == null) {
      assertEquals(null, result);
    } else {
      assertEquals(expected, result.toString());
    }

    // run the test on both a list and as a single pattern just to
    // make sure the underlying code works for both
    Pair<TregexPattern, TsurgeonPattern> surgery = new Pair<>(tregex, tsurgeon);
    runTest(Collections.singletonList(surgery), input, expected);
  }

  public static void runTest(List<Pair<TregexPattern, TsurgeonPattern>> surgery,
                      String input, String expected) {
    Tree result = Tsurgeon.processPatternsOnTree(surgery, treeFromString(input));
    if (expected == null) {
      assertEquals(null, result);
    } else {
      assertEquals(expected, result.toString());
    }
  }

  public static void outputResults(TregexPattern tregex, TsurgeonPattern tsurgeon,
                            String input, String expected) {
    outputResults(tregex, tsurgeon, input);
  }

  public static void outputResults(TregexPattern tregex, TsurgeonPattern tsurgeon,
                            String input) {
    System.out.println("Tsurgeon: " + tsurgeon);
    System.out.println("Tregex: " + tregex);
    TregexMatcher m = tregex.matcher(treeFromString(input));
    if (m.find()) {
      System.err.println(" Matched");
    } else {
      System.err.println(" Did not match");
    }
    Tree result = Tsurgeon.processPattern(tregex, tsurgeon, treeFromString(input));
    System.out.println(result);
  }
}
