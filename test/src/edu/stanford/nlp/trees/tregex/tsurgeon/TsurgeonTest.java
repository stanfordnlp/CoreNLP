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
    Tree updated = tsurgeon.evaluate(tree, matcher);
    assertEquals("(A (FOO (BAR 1 2)))", updated.toString());
    // TODO: do we want the tsurgeon to implicitely update the matched node?
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
    Tree updated = tsurgeon.evaluate(tree, matcher);
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
    Tree updated = tsurgeon.evaluate(tree, matcher);
    assertEquals("(A (FOO (B 1 2)))", updated.toString());
    assertEquals("(B 1 2)", matcher.getNode("foo").toString());
    assertFalse(matcher.find());
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

    tsurgeon = Tsurgeon.parseOperation("relabel foo /\\//");
    tregex = TregexPattern.compile("B=foo");
    runTest(tregex, tsurgeon, "(A (B 0) (C 1))", "(A (/ 0) (C 1))");
    runTest(tregex, tsurgeon, "(A (B 0) (B 1))", "(A (/ 0) (/ 1))");

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
  }

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
    List<Pair<TregexPattern, TsurgeonPattern>> surgery =
      new ArrayList<Pair<TregexPattern, TsurgeonPattern>>();

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

  public void runTest(TregexPattern tregex, TsurgeonPattern tsurgeon,
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
    Pair<TregexPattern, TsurgeonPattern> surgery = 
      new Pair<TregexPattern, TsurgeonPattern>(tregex, tsurgeon);
    runTest(Collections.singletonList(surgery), input, expected);
  }

  public void runTest(List<Pair<TregexPattern, TsurgeonPattern>> surgery,
                      String input, String expected) {
    Tree result = Tsurgeon.processPatternsOnTree(surgery, treeFromString(input));
    if (expected == null) {
      assertEquals(null, result);
    } else {
      assertEquals(expected, result.toString());
    }
  }

  public void outputResults(TregexPattern tregex, TsurgeonPattern tsurgeon,
                            String input, String expected) {
    outputResults(tregex, tsurgeon, input);
  }

  public void outputResults(TregexPattern tregex, TsurgeonPattern tsurgeon,
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
