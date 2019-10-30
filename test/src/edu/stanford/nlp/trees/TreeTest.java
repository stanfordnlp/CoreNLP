package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.Label;

import junit.framework.TestCase;

import java.util.Set;
import java.util.HashSet;

/**
 * Test Tree.java
 *
 * @author Christopher Manning
 */
public class TreeTest extends TestCase {

  /** Test that using an iterator() straight off a tree gives the same
   *  results as building a subTrees collection and then doing an
   *  iterator off of that.
   */
  @SuppressWarnings("null")
  public void testTreeIterator() {
    Tree t = Tree.valueOf("(ROOT (S (NP (DT The) (ADJP (RB very) (JJ proud)) (NN woman)) (VP (VBD yawned) (ADVP (RB loudly))) (. .)))");
    if (t == null) {
      fail("testTreeIterator failed to construct tree");
    }
    Set<Tree> m1 = new HashSet<>();
    Set<Tree> m2 = new HashSet<>();
    // build iterator List
    for (Tree sub : t) {
      m1.add(sub);
    }
    for (Tree sub : t.subTrees()) {
      m2.add(sub);
    }
    assertEquals(m1, m2);
  }

  @SuppressWarnings("null")
  public void testDeeperCopy() {
    Tree t1 = null;
    try {
      t1 = Tree.valueOf("(ROOT (S (NP I) (VP ran)))");
    } catch (Exception e) {
      e.printStackTrace();
    }
    if (t1 == null) {
      fail("testDeeperCopy failed to construct tree");
    }
    Tree t2 = t1.deepCopy();
    assertEquals(t1, t2);               // make sure trees are equal
    assertTrue(t1 != t2);               // make sure trees are not ==
    Label l1 = t1.firstChild().firstChild().firstChild().label();
    Label l2 = t2.firstChild().firstChild().firstChild().label();
    assertEquals(l1, l2);               // make sure labels are equal (redundant)
    assertTrue(l1 != l2);               // make sure labels are not ==
  }

  public void testRemove() {
    Tree t = Tree.valueOf("(ROOT (S (NP (DT The) (ADJP (RB very) (JJ proud)) (NN woman)) (VP (VBD yawned) (ADVP (RB loudly))) (. .)))");
    Tree kid = t.firstChild();
    try {
      t.remove(kid);
      fail("Tree remove should be unimplemented.");
    } catch (Exception e) {
      // we're good
    }
    try {
      t.remove(kid);
      fail("Tree removeAll should be unimplemented.");
    } catch (Exception e) {
      // we're good
    }
    kid.removeChild(0);
    assertEquals("(ROOT (S (VP (VBD yawned) (ADVP (RB loudly))) (. .)))", t.toString());
    t.removeChild(0);
    assertEquals("ROOT", t.toString());
  }


  public void testDominates() {
    Tree t = Tree.valueOf("(A (B this) (C (D is) (E a) (F small)) (G test))");
    assertFalse(t.dominates(t));

    for (Tree child : t.children()) {
      assertTrue(t.dominates(child));
      assertFalse(child.dominates(t));
    }
  }

  public void testPennPrint() {
    // a Label with a null value should print as "" not null.
    Tree t = Tree.valueOf("( (SBARQ (WHNP (WP What)) (SQ (VBP are) (NP (DT the) (NNP Valdez) (NNS Principles))) (. ?)))",
            new LabeledScoredTreeReaderFactory(new TreeNormalizer()));
    assertNull("Root of tree should have null label if none in String", t.label().value());

    String separator = System.getProperty("line.separator");
    String answer = ("( (SBARQ" + separator +
                     "    (WHNP (WP What))" + separator +
                     "    (SQ (VBP are)" + separator +
                     "      (NP (DT the) (NNP Valdez) (NNS Principles)))" + separator +
                     "    (. ?)))" + separator);
    assertEquals(answer, t.pennString());
  }

  public void checkBinary(String treeString, boolean expected) {
    Tree tree = Tree.valueOf(treeString);
    boolean answer = tree.isBinary();
    assertEquals("Got " + answer + " instead of " + expected + " for " + treeString,
                 expected, answer);
  }

  public void testIsBinary() {
    checkBinary("(5)", true);
    checkBinary("(1 5)", true);
    checkBinary("(1 (2 5) (3 4))", true);
    checkBinary("(1 (2 5))", false);
    checkBinary("(1 (2 3) (4 5) (6 7))", false);
    checkBinary("(1 (2 3) (4 (5 6) (6 7)))", true);
  }
}
