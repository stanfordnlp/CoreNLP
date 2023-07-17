package edu.stanford.nlp.trees.tregex;

import junit.framework.TestCase;

import java.io.IOException;
import java.io.StringReader;
import java.util.IdentityHashMap;
import java.util.Set;

import edu.stanford.nlp.trees.*;
import java.util.function.Function;

public class TregexTest extends TestCase {

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

  public static Tree[] treesFromString(String ... s) {
    Tree[] trees = new Tree[s.length];
    for (int i = 0; i < s.length; ++i) {
      trees[i] = treeFromString(s[i]);
    }
    return trees;
  }

  /** This was buggy in 2010. But John Bauer fixed it. */
  public void testJoãoSilva() {
    final TregexPattern tregex1 = TregexPattern.compile(
            "PNT=p >>- (__=l >, (__=t <- (__=r <, __=m <- (__ <, CONJ <- __=z))))");
    final TregexPattern tregex2 = TregexPattern.compile(
            "PNT=p >>- (/(.+)/#1%var=l >, (__=t <- (__=r <, /(.+)/#1%var=m <- (__ <, CONJ <- /(.+)/#1%var=z))))");
    final TregexPattern tregex3 = TregexPattern.compile(
            "PNT=p >>- (__=l >, (__=t <- (__=r <, ~l <- (__ <, CONJ <- ~l))))");
    Tree tree = treeFromString("(T (X (N (N Moe (PNT ,)))) (NP (X (N Curly)) (NP (CONJ and) (X (N Larry)))))");
    TregexMatcher matcher1 = tregex1.matcher(tree);
    assertTrue(matcher1.find());
    TregexMatcher matcher2 = tregex2.matcher(tree);
    assertTrue(matcher2.find());
    TregexMatcher matcher3 = tregex3.matcher(tree);
    assertTrue(matcher3.find());
  }

  public void testNoResults() {
    final TregexPattern pMWE = TregexPattern.compile("/^MW/");
    Tree tree = treeFromString("(Foo)");
    TregexMatcher matcher = pMWE.matcher(tree);
    assertFalse(matcher.find());
  }

  public void testOneResult() {
    final TregexPattern pMWE = TregexPattern.compile("/^MW/");
    Tree tree = treeFromString("(ROOT (MWE (N 1) (N 2) (N 3)))");
    TregexMatcher matcher = pMWE.matcher(tree);
    assertTrue(matcher.find());
    Tree match = matcher.getMatch();
    assertEquals("(MWE (N 1) (N 2) (N 3))", match.toString());
    assertFalse(matcher.find());
  }

  public void testTwoResults() {
    final TregexPattern pMWE = TregexPattern.compile("/^MW/");
    Tree tree = treeFromString("(ROOT (MWE (N 1) (N 2) (N 3)) (MWV (A B)))");
    TregexMatcher matcher = pMWE.matcher(tree);

    assertTrue(matcher.find());
    Tree match = matcher.getMatch();
    assertEquals("(MWE (N 1) (N 2) (N 3))", match.toString());

    assertTrue(matcher.find());
    match = matcher.getMatch();
    assertEquals("(MWV (A B))", match.toString());

    assertFalse(matcher.find());
  }

  /**
   * a tregex pattern should be able to go more than once.
   * just like me.
   */
  public void testReuse() {
    final TregexPattern pMWE = TregexPattern.compile("/^MW/");

    Tree tree = treeFromString("(ROOT (MWE (N 1) (N 2) (N 3)) (MWV (A B)))");
    TregexMatcher matcher = pMWE.matcher(tree);
    assertTrue(matcher.find());
    assertTrue(matcher.find());
    assertFalse(matcher.find());

    tree = treeFromString("(ROOT (MWE (N 1) (N 2) (N 3)))");
    matcher = pMWE.matcher(tree);
    assertTrue(matcher.find());
    assertFalse(matcher.find());

    tree = treeFromString("(Foo)");
    matcher = pMWE.matcher(tree);
    assertFalse(matcher.find());
  }

  /**
   * reruns one of the simpler tests using the test class to make sure
   * the test class works
   */
  public void testTest() {
    runTest("/^MW/", "(ROOT (MWE (N 1) (N 2) (N 3)) (MWV (A B)))",
            "(MWE (N 1) (N 2) (N 3))", "(MWV (A B))");
  }

  public void testWordDisjunction() {
    TregexPattern pattern = TregexPattern.compile("a|b|c << bar");
    runTest(pattern, "(a (bar 1))", "(a (bar 1))");
    runTest(pattern, "(b (bar 1))", "(b (bar 1))");
    runTest(pattern, "(c (bar 1))", "(c (bar 1))");
    runTest(pattern, "(d (bar 1))");
    runTest(pattern, "(e (bar 1))");
    runTest(pattern, "(f (bar 1))");
    runTest(pattern, "(g (bar 1))");

    pattern = TregexPattern.compile("a|b|c|d|e|f << bar");
    runTest(pattern, "(a (bar 1))", "(a (bar 1))");
    runTest(pattern, "(b (bar 1))", "(b (bar 1))");
    runTest(pattern, "(c (bar 1))", "(c (bar 1))");
    runTest(pattern, "(d (bar 1))", "(d (bar 1))");
    runTest(pattern, "(e (bar 1))", "(e (bar 1))");
    runTest(pattern, "(f (bar 1))", "(f (bar 1))");
    runTest(pattern, "(g (bar 1))");
  }

  public void testDominates() {
    final TregexPattern dominatesPattern =
      TregexPattern.compile("foo << bar");
    runTest(dominatesPattern, "(foo (bar 1))", "(foo (bar 1))");
    runTest(dominatesPattern, "(foo (a (bar 1)))", "(foo (a (bar 1)))");
    runTest(dominatesPattern, "(foo (a (b (bar 1))))", "(foo (a (b (bar 1))))");
    runTest(dominatesPattern, "(foo (a (b 1) (bar 2)))",
            "(foo (a (b 1) (bar 2)))");
    runTest(dominatesPattern, "(foo (a (b 1) (c 2) (bar 3)))",
            "(foo (a (b 1) (c 2) (bar 3)))");
    runTest(dominatesPattern, "(foo (baz 1))");
    runTest(dominatesPattern, "(a (foo (bar 1)))", "(foo (bar 1))");
    runTest(dominatesPattern, "(a (foo (baz (bar 1))))",
            "(foo (baz (bar 1)))");
    runTest(dominatesPattern, "(a (foo (bar 1)) (foo (bar 2)))",
            "(foo (bar 1))", "(foo (bar 2))");

    final TregexPattern dominatedPattern =
      TregexPattern.compile("foo >> bar");
    runTest(dominatedPattern, "(foo (bar 1))");
    runTest(dominatedPattern, "(foo (a (bar 1)))");
    runTest(dominatedPattern, "(foo (a (b (bar 1))))");
    runTest(dominatedPattern, "(foo (a (b 1) (bar 2)))");
    runTest(dominatedPattern, "(foo (a (b 1) (c 2) (bar 3)))");
    runTest(dominatedPattern, "(bar (foo 1))", "(foo 1)");
    runTest(dominatedPattern, "(bar (a (foo 1)))", "(foo 1)");
    runTest(dominatedPattern, "(bar (a (foo (b 1))))", "(foo (b 1))");
    runTest(dominatedPattern, "(bar (a (foo 1) (foo 2)))",
            "(foo 1)", "(foo 2)");
    runTest(dominatedPattern, "(bar (foo (foo 1)))", "(foo (foo 1))", "(foo 1)");
    runTest(dominatedPattern, "(a (bar (foo 1)))", "(foo 1)");
  }

  public void testImmediatelyDominates() {
    final TregexPattern dominatesPattern =
      TregexPattern.compile("foo < bar");
    runTest(dominatesPattern, "(foo (bar 1))", "(foo (bar 1))");
    runTest(dominatesPattern, "(foo (a (bar 1)))");
    runTest(dominatesPattern, "(a (foo (bar 1)))", "(foo (bar 1))");
    runTest(dominatesPattern, "(a (foo (baz 1) (bar 2)))",
            "(foo (baz 1) (bar 2))");
    runTest(dominatesPattern, "(a (foo (bar 1)) (foo (bar 2)))",
            "(foo (bar 1))", "(foo (bar 2))");

    final TregexPattern dominatedPattern =
      TregexPattern.compile("foo > bar");

    runTest(dominatedPattern, "(foo (bar 1))");
    runTest(dominatedPattern, "(foo (a (bar 1)))");
    runTest(dominatedPattern, "(foo (a (b (bar 1))))");
    runTest(dominatedPattern, "(foo (a (b 1) (bar 2)))");
    runTest(dominatedPattern, "(foo (a (b 1) (c 2) (bar 3)))");
    runTest(dominatedPattern, "(bar (foo 1))", "(foo 1)");
    runTest(dominatedPattern, "(bar (a (foo 1)))");
    runTest(dominatedPattern, "(bar (foo 1) (foo 2))", "(foo 1)", "(foo 2)");
    runTest(dominatedPattern, "(bar (foo (foo 1)))", "(foo (foo 1))");
    runTest(dominatedPattern, "(a (bar (foo 1)))", "(foo 1)");
  }

  public void testSister() {
    TregexPattern pattern = TregexPattern.compile("/.*/ $ foo");
    runTest(pattern, "(a (foo 1) (bar 2))", "(bar 2)");
    runTest(pattern, "(a (bar 1) (foo 2))", "(bar 1)");
    runTest(pattern, "(a (foo 1) (bar 2) (baz 3))", "(bar 2)", "(baz 3)");
    runTest(pattern, "(a (foo (bar 2)) (baz 3))", "(baz 3)");
    runTest(pattern, "(a (foo (bar 2)) (baz (bif 3)))", "(baz (bif 3))");
    runTest(pattern, "(a (foo (bar 2)))");
    runTest(pattern, "(a (foo 1))");

    pattern = TregexPattern.compile("bar|baz $ foo");
    runTest(pattern, "(a (foo 1) (bar 2))", "(bar 2)");
    runTest(pattern, "(a (bar 1) (foo 2))", "(bar 1)");
    runTest(pattern, "(a (foo 1) (bar 2) (baz 3))", "(bar 2)", "(baz 3)");
    runTest(pattern, "(a (foo (bar 2)) (baz 3))", "(baz 3)");
    runTest(pattern, "(a (foo (bar 2)) (baz (bif 3)))", "(baz (bif 3))");
    runTest(pattern, "(a (foo (bar 2)))");
    runTest(pattern, "(a (foo 1))");

    pattern = TregexPattern.compile("/.*/ $ foo");
    runTest(pattern, "(a (foo 1) (foo 2))", "(foo 1)", "(foo 2)");
    runTest(pattern, "(a (foo 1))");

    pattern = TregexPattern.compile("foo $ foo");
    runTest(pattern, "(a (foo 1) (foo 2))", "(foo 1)", "(foo 2)");
    runTest(pattern, "(a (foo 1))");

    pattern = TregexPattern.compile("foo $ foo=a");
    Tree tree = treeFromString("(a (foo 1) (foo 2) (foo 3))");
    TregexMatcher matcher = pattern.matcher(tree);
    assertTrue(matcher.find());
    assertEquals("(foo 1)", matcher.getMatch().toString());
    assertEquals("(foo 2)", matcher.getNode("a").toString());
    assertTrue(matcher.find());
    assertEquals("(foo 1)", matcher.getMatch().toString());
    assertEquals("(foo 3)", matcher.getNode("a").toString());
    assertTrue(matcher.find());
    assertEquals("(foo 2)", matcher.getMatch().toString());
    assertEquals("(foo 1)", matcher.getNode("a").toString());
    assertTrue(matcher.find());
    assertEquals("(foo 2)", matcher.getMatch().toString());
    assertEquals("(foo 3)", matcher.getNode("a").toString());
    assertTrue(matcher.find());
    assertEquals("(foo 3)", matcher.getMatch().toString());
    assertEquals("(foo 1)", matcher.getNode("a").toString());
    assertTrue(matcher.find());
    assertEquals("(foo 3)", matcher.getMatch().toString());
    assertEquals("(foo 2)", matcher.getNode("a").toString());
    assertFalse(matcher.find());

    runTest("foo $ foo", "(a (foo 1))");
  }

  public void testPrecedesFollows() {
    TregexPattern pattern = TregexPattern.compile("/.*/ .. foo");

    runTest(pattern, "(a (foo 1) (bar 2))");
    runTest(pattern, "(a (bar 1) (foo 2))", "(bar 1)", "(1)");
    runTest(pattern, "(a (bar 1) (baz 2) (foo 3))",
            "(bar 1)", "(1)", "(baz 2)", "(2)");
    runTest(pattern, "(a (foo 1) (baz 2) (bar 3))");
    runTest(pattern, "(a (bar (foo 1)) (baz 2))");
    runTest(pattern, "(a (bar 1) (baz (foo 2)))", "(bar 1)", "(1)");
    runTest(pattern, "(a (bar 1) (baz 2) (bif (foo 3)))",
            "(bar 1)", "(1)", "(baz 2)", "(2)");
    runTest(pattern, "(a (bar (foo 1)) (baz 2) (bif 3))");
    runTest(pattern, "(a (bar 1) (baz 2) (foo (bif 3)))",
            "(bar 1)", "(1)", "(baz 2)", "(2)");
    runTest(pattern, "(a (bar 1) (foo (bif 2)) (baz 3))",
            "(bar 1)", "(1)");

    pattern = TregexPattern.compile("/.*/ ,, foo");

    runTest(pattern, "(a (foo 1) (bar 2))", "(bar 2)", "(2)");
    runTest(pattern, "(a (bar 1) (foo 2))");
    runTest(pattern, "(a (bar 1) (baz 2) (foo 3))");
    runTest(pattern, "(a (foo 1) (baz 2) (bar 3))",
            "(baz 2)", "(2)", "(bar 3)", "(3)");
    runTest(pattern, "(a (bar (foo 1)) (baz 2))", "(baz 2)", "(2)");
    runTest(pattern, "(a (bar 1) (baz (foo 2)))");
    runTest(pattern, "(a (bar 1) (baz 2) (bif (foo 3)))");
    runTest(pattern, "(a (bar (foo 1)) (baz 2) (bif 3))",
            "(baz 2)", "(2)", "(bif 3)", "(3)");
    runTest(pattern, "(a (bar 1) (baz 2) (foo (bif 3)))");
    runTest(pattern, "(a (foo (bif 1)) (bar 2) (baz 3))",
            "(bar 2)", "(2)", "(baz 3)", "(3)");
    runTest(pattern, "(a (bar 1) (foo (bif 2)) (baz 3))",
            "(baz 3)", "(3)");
  }

  public void testImmediatePrecedesFollows() {
    // immediate precedes
    TregexPattern pattern = TregexPattern.compile("/.*/ . foo");

    runTest(pattern, "(a (foo 1) (bar 2))");
    runTest(pattern, "(a (bar 1) (foo 2))", "(bar 1)", "(1)");
    runTest(pattern, "(a (bar 1) (baz 2) (foo 3))", "(baz 2)", "(2)");
    runTest(pattern, "(a (foo 1) (baz 2) (bar 3))");
    runTest(pattern, "(a (bar (foo 1)) (baz 2))");
    runTest(pattern, "(a (bar 1) (baz (foo 2)))", "(bar 1)", "(1)");
    runTest(pattern, "(a (bar 1) (baz 2) (bif (foo 3)))", "(baz 2)", "(2)");
    runTest(pattern, "(a (bar (foo 1)) (baz 2) (bif 3))");
    runTest(pattern, "(a (bar 1) (baz 2) (foo (bif 3)))", "(baz 2)", "(2)");
    runTest(pattern, "(a (bar 1) (foo (bif 2)) (baz 3))", "(bar 1)", "(1)");
    runTest(pattern, "(a (bar 1) (foo 2) (baz 3) (foo 4) (bif 5))",
            "(bar 1)", "(1)", "(baz 3)", "(3)");
    runTest(pattern, "(a (bar 1) (foo 2) (foo 3) (baz 4))",
            "(bar 1)", "(1)", "(foo 2)", "(2)");
    runTest(pattern, "(a (b (c 1) (d 2)) (foo))",
            "(b (c 1) (d 2))", "(d 2)", "(2)");
    runTest(pattern, "(a (b (c 1) (d 2)) (bar (foo 3)))",
            "(b (c 1) (d 2))", "(d 2)", "(2)");
    runTest(pattern, "(a (b (c 1) (d 2)) (bar (baz 3) (foo 4)))",
            "(baz 3)", "(3)");
    runTest(pattern, "(a (b (c 1) (d 2)) (bar (baz 2 3) (foo 4)))",
            "(baz 2 3)", "(3)");

    // immediate follows
    pattern = TregexPattern.compile("/.*/ , foo");

    runTest(pattern, "(a (foo 1) (bar 2))", "(bar 2)", "(2)");
    runTest(pattern, "(a (bar 1) (foo 2))");
    runTest(pattern, "(a (bar 1) (baz 2) (foo 3))");
    runTest(pattern, "(a (foo 1) (baz 2) (bar 3))", "(baz 2)", "(2)");
    runTest(pattern, "(a (bar (foo 1)) (baz 2))", "(baz 2)", "(2)");
    runTest(pattern, "(a (bar 1) (baz (foo 2)))");
    runTest(pattern, "(a (bar 1) (baz 2) (bif (foo 3)))");
    runTest(pattern, "(a (bar (foo 1)) (baz 2) (bif 3))", "(baz 2)", "(2)");
    runTest(pattern, "(a (bar 1) (baz 2) (foo (bif 3)))");
    runTest(pattern, "(a (foo (bif 1)) (bar 2) (baz 3))", "(bar 2)", "(2)");
    runTest(pattern, "(a (bar 1) (foo (bif 2)) (baz 3))", "(baz 3)", "(3)");
    runTest(pattern, "(a (bar 1) (foo 2) (baz 3) (foo 4) (bif 5))",
            "(baz 3)", "(3)", "(bif 5)", "(5)");
    runTest(pattern, "(a (bar 1) (foo 2) (foo 3) (baz 4))",
            "(foo 3)", "(3)", "(baz 4)", "(4)");
    runTest(pattern, "(a (foo) (b (c 1) (d 2)))",
            "(b (c 1) (d 2))", "(c 1)", "(1)");
    runTest(pattern, "(a (bar (foo 3)) (b (c 1) (d 2)))",
            "(b (c 1) (d 2))", "(c 1)", "(1)");
    runTest(pattern, "(a (bar (baz 3) (foo 4)) (b (c 1) (d 2)))",
            "(b (c 1) (d 2))", "(c 1)", "(1)");
    runTest(pattern, "(a (bar (foo 4) (baz 3)) (b (c 1) (d 2)))",
            "(baz 3)", "(3)");
  }

  public void testLeftRightMostDescendant() {
    // B leftmost descendant of A
    runTest("/.*/ <<, /1/", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(a (foo 1 2) (bar 3 4))", "(foo 1 2)");
    runTest("/.*/ <<, /2/", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))");
    runTest("/.*/ <<, foo", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(a (foo 1 2) (bar 3 4))");
    runTest("/.*/ <<, baz", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(b (baz 5))");

    // B rightmost descendant of A
    runTest("/.*/ <<- /1/", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))");
    runTest("/.*/ <<- /2/", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(foo 1 2)");
    runTest("/.*/ <<- /4/", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(a (foo 1 2) (bar 3 4))", "(bar 3 4)");

    // A leftmost descendant of B
    runTest("/.*/ >>, root", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(a (foo 1 2) (bar 3 4))", "(foo 1 2)", "(1)");
    runTest("/.*/ >>, a", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(foo 1 2)", "(1)");
    runTest("/.*/ >>, bar", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(3)");

    // A rightmost descendant of B
    runTest("/.*/ >>- root", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(b (baz 5))", "(baz 5)", "(5)");
    runTest("/.*/ >>- a", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(bar 3 4)", "(4)");
    runTest("/.*/ >>- /1/", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))");
  }

  public void testFirstLastChild() {
    // A is the first child of B
    runTest("/.*/ >, root", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(a (foo 1 2) (bar 3 4))");
    runTest("/.*/ >, a", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(foo 1 2)");
    runTest("/.*/ >, foo", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(1)");
    runTest("/.*/ >, bar", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(3)");

    // A is the last child of B
    runTest("/.*/ >- root", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(b (baz 5))");
    runTest("/.*/ >- a", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(bar 3 4)");
    runTest("/.*/ >- foo", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(2)");
    runTest("/.*/ >- bar", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(4)");
    runTest("/.*/ >- b", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(baz 5)");

    // B is the first child of A
    runTest("/.*/ <, root", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))");
    runTest("/.*/ <, a", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))");
    runTest("/.*/ <, /1/", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(foo 1 2)");
    runTest("/.*/ <, /2/", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))");
    runTest("/.*/ <, bar", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))");
    runTest("/.*/ <, /3/", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(bar 3 4)");
    runTest("/.*/ <, /4/", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))");

    // B is the last child of A
    runTest("/.*/ <- root", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))");
    runTest("/.*/ <- a", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))");
    runTest("/.*/ <- /1/", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))");
    runTest("/.*/ <- /2/", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(foo 1 2)");
    runTest("/.*/ <- bar", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(a (foo 1 2) (bar 3 4))");
    runTest("/.*/ <- /3/", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))");
    runTest("/.*/ <- /4/", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(bar 3 4)");
  }

  public void testIthChild() {
    // A is the ith child of B
    runTest("/.*/ >1 root", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(a (foo 1 2) (bar 3 4))");
    runTest("/.*/ >1 a", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(foo 1 2)");
    runTest("/.*/ >2 a", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(bar 3 4)");
    runTest("/.*/ >1 foo", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(1)");
    runTest("/.*/ >2 foo", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(2)");
    runTest("/.*/ >1 bar", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(3)");
    runTest("/.*/ >2 bar", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(4)");

    // A is the -ith child of B
    runTest("/.*/ >-1 root", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(b (baz 5))");
    runTest("/.*/ >-1 a", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(bar 3 4)");
    runTest("/.*/ >-2 a", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(foo 1 2)");
    runTest("/.*/ >-1 foo", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(2)");
    runTest("/.*/ >-2 bar", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(3)");
    runTest("/.*/ >-1 bar", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(4)");
    runTest("/.*/ >-1 b", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(baz 5)");
    runTest("/.*/ >-2 b", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))");

    // B is the ith child of A
    runTest("/.*/ <1 root", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))");
    runTest("/.*/ <1 a", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))");
    runTest("/.*/ <1 /1/", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(foo 1 2)");
    runTest("/.*/ <1 /2/", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))");
    runTest("/.*/ <1 bar", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))");
    runTest("/.*/ <2 bar", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(a (foo 1 2) (bar 3 4))");
    runTest("/.*/ <3 bar", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))");
    runTest("/.*/ <1 /3/", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(bar 3 4)");
    runTest("/.*/ <1 /4/", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))");
    runTest("/.*/ <2 /4/", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(bar 3 4)");

    // B is the -ith child of A
    runTest("/.*/ <-1 root", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))");
    runTest("/.*/ <-1 a", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))");
    runTest("/.*/ <-2 a", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))");
    runTest("/.*/ <-1 /1/", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))");
    runTest("/.*/ <-2 /1/", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(foo 1 2)");
    runTest("/.*/ <-1 /2/", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(foo 1 2)");
    runTest("/.*/ <-2 /2/", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))");
    runTest("/.*/ <-1 bar", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(a (foo 1 2) (bar 3 4))");
    runTest("/.*/ <-1 /3/", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))");
    runTest("/.*/ <-2 /3/", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(bar 3 4)");
    runTest("/.*/ <-1 /4/", "(root (a (foo 1 2) (bar 3 4)) (b (baz 5)))",
            "(bar 3 4)");
  }

  public void testOnlyChild() {
    runTest("foo <: bar", "(foo (bar 1))", "(foo (bar 1))");
    runTest("foo <: bar", "(foo (bar 1) (bar 2))");
    runTest("foo <: bar", "(foo)");
    runTest("foo <: bar", "(foo (baz (bar)))");
    runTest("foo <: bar", "(foo 1)");

    runTest("bar >: foo", "(foo (bar 1))", "(bar 1)");
    runTest("bar >: foo", "(foo (bar 1) (bar 2))");
    runTest("bar >: foo", "(foo)");
    runTest("bar >: foo", "(foo (baz (bar)))");
    runTest("bar >: foo", "(bar (foo 1))");

    runTest("/.*/ >: foo", "(a (foo (bar 1)) (foo (baz 2)))",
            "(bar 1)", "(baz 2)");
  }

  public void testDominateUnaryChain() {
    runTest("foo <<: bar", "(a (foo (b (c (d (bar))))))",
            "(foo (b (c (d (bar)))))");
    runTest("foo <<: bar", "(a (foo (b (c (d (bar) (baz))))))");
    runTest("foo <<: bar", "(a (foo (b (c (d (bar)) (baz)))))");
    runTest("foo <<: bar", "(a (foo (b (c (d (bar))) (baz))))");
    runTest("foo <<: bar", "(a (foo (b (c (d (bar)))) (baz)))");
    runTest("foo <<: bar", "(a (foo (b (c (d (bar))))) (baz))",
            "(foo (b (c (d (bar)))))");
    runTest("foo <<: bar", "(a (foo (b (c (bar)))))",
            "(foo (b (c (bar))))");
    runTest("foo <<: bar", "(a (foo (b (bar))))",
            "(foo (b (bar)))");
    runTest("foo <<: bar", "(a (foo (bar)))",
            "(foo (bar))");

    runTest("bar >>: foo", "(a (foo (b (c (d (bar))))))", "(bar)");
    runTest("bar >>: foo", "(a (foo (b (c (d (bar) (baz))))))");
    runTest("bar >>: foo", "(a (foo (b (c (d (bar)) (baz)))))");
    runTest("bar >>: foo", "(a (foo (b (c (d (bar))) (baz))))");
    runTest("bar >>: foo", "(a (foo (b (c (d (bar)))) (baz)))");
    runTest("bar >>: foo", "(a (foo (b (c (d (bar))))) (baz))", "(bar)");
    runTest("bar >>: foo", "(a (foo (b (c (bar)))))", "(bar)");
    runTest("bar >>: foo", "(a (foo (b (bar))))", "(bar)");
    runTest("bar >>: foo", "(a (foo (bar)))", "(bar)");
  }

  public void testPrecedingFollowingSister() {
    // test preceding sisters
    TregexPattern preceding = TregexPattern.compile("/.*/ $.. baz");
    runTest(preceding, "(a (foo 1) (bar 2) (baz 3))", "(foo 1)", "(bar 2)");
    runTest(preceding, "(root (b (foo 1)) (a (foo 1) (bar 2) (baz 3)))",
            "(foo 1)", "(bar 2)");
    runTest(preceding, "(root (a (foo 1) (bar 2) (baz 3)) (b (foo 1)))",
            "(foo 1)", "(bar 2)");
    runTest(preceding, "(a (foo 1) (baz 2) (bar 3))", "(foo 1)");
    runTest(preceding, "(a (baz 1) (foo 2) (bar 3))");

    // test immediately preceding sisters
    TregexPattern impreceding = TregexPattern.compile("/.*/ $. baz");
    runTest(impreceding, "(a (foo 1) (bar 2) (baz 3))", "(bar 2)");
    runTest(impreceding, "(root (b (foo 1)) (a (foo 1) (bar 2) (baz 3)))",
            "(bar 2)");
    runTest(impreceding, "(root (a (foo 1) (bar 2) (baz 3)) (b (foo 1)))",
            "(bar 2)");
    runTest(impreceding, "(a (foo 1) (baz 2) (bar 3))", "(foo 1)");
    runTest(impreceding, "(a (baz 1) (foo 2) (bar 3))");

    // test following sisters
    TregexPattern following = TregexPattern.compile("/.*/ $,, baz");

    runTest(following, "(a (foo 1) (bar 2) (baz 3))");
    runTest(following, "(root (b (foo 1)) (a (foo 1) (bar 2) (baz 3)))");
    runTest(following, "(root (a (foo 1) (bar 2) (baz 3)) (b (foo 1)))");
    runTest(following, "(root (a (baz 1) (bar 2) (foo 3)) (b (foo 1)))",
            "(bar 2)", "(foo 3)");
    runTest(following, "(a (foo 1) (baz 2) (bar 3))", "(bar 3)");
    runTest(following, "(a (baz 1) (foo 2) (bar 3))", "(foo 2)", "(bar 3)");

    // test immediately following sisters
    TregexPattern imfollowing = TregexPattern.compile("/.*/ $, baz");
    runTest(imfollowing, "(a (foo 1) (bar 2) (baz 3))");
    runTest(imfollowing, "(root (b (foo 1)) (a (foo 1) (bar 2) (baz 3)))");
    runTest(imfollowing, "(root (a (foo 1) (bar 2) (baz 3)) (b (foo 1)))");
    runTest(imfollowing, "(root (a (baz 1) (bar 2) (foo 3)) (b (foo 1)))",
            "(bar 2)");
    runTest(imfollowing, "(a (foo 1) (baz 2) (bar 3))", "(bar 3)");
    runTest(imfollowing, "(a (baz 1) (foo 2) (bar 3))", "(foo 2)");
  }

  public void testCategoryFunctions() {
    Function<String, String> fooCategory = new Function<String, String>() {
      public String apply(String label) {
        if (label == null) {
          return label;
        }
        if (label.equals("bar")) {
          return "foo";
        }
        return label;
      }
    };
    TregexPatternCompiler fooCompiler = new TregexPatternCompiler(fooCategory);

    TregexPattern fooTregex = fooCompiler.compile("@foo > bar");
    runTest(fooTregex, "(bar (foo 0))", "(foo 0)");
    runTest(fooTregex, "(bar (bar 0))", "(bar 0)");
    runTest(fooTregex, "(foo (foo 0))");
    runTest(fooTregex, "(foo (bar 0))");

    Function<String, String> barCategory = new Function<String, String>() {
      public String apply(String label) {
        if (label == null) {
          return label;
        }
        if (label.equals("foo")) {
          return "bar";
        }
        return label;
      }
    };
    TregexPatternCompiler barCompiler = new TregexPatternCompiler(barCategory);

    TregexPattern barTregex = barCompiler.compile("@bar > foo");
    runTest(barTregex, "(bar (foo 0))");
    runTest(barTregex, "(bar (bar 0))");
    runTest(barTregex, "(foo (foo 0))", "(foo 0)");
    runTest(barTregex, "(foo (bar 0))", "(bar 0)");

    // These should still work, since the tregex patterns have
    // different category functions.  Old enough versions of tregex do
    // not allow for that.
    runTest(fooTregex, "(bar (foo 0))", "(foo 0)");
    runTest(fooTregex, "(bar (bar 0))", "(bar 0)");
    runTest(fooTregex, "(foo (foo 0))");
    runTest(fooTregex, "(foo (bar 0))");
  }

  public void testCategoryDisjunction() {
    Function<String, String> abCategory = new Function<String, String>() {
      public String apply(String label) {
        if (label == null) {
          return label;
        }
        if (label.startsWith("a")) {
          return "aaa";
        }
        if (label.startsWith("b")) {
          return "bbb";
        }
        return label;
      }
    };
    TregexPatternCompiler abCompiler = new TregexPatternCompiler(abCategory);

    TregexPattern aaaTregex = abCompiler.compile("foo > @aaa");
    runTest(aaaTregex, "(aaa (foo 0))", "(foo 0)");
    runTest(aaaTregex, "(abc (foo 0))", "(foo 0)");
    runTest(aaaTregex, "(bbb (foo 0))");
    runTest(aaaTregex, "(bcd (foo 0))");
    runTest(aaaTregex, "(ccc (foo 0))");

    TregexPattern bbbTregex = abCompiler.compile("foo > @bbb");
    runTest(bbbTregex, "(aaa (foo 0))");
    runTest(bbbTregex, "(abc (foo 0))");
    runTest(bbbTregex, "(bbb (foo 0))", "(foo 0)");
    runTest(bbbTregex, "(bcd (foo 0))", "(foo 0)");
    runTest(bbbTregex, "(ccc (foo 0))");

    TregexPattern bothTregex = abCompiler.compile("foo > @aaa|bbb");
    runTest(bothTregex, "(aaa (foo 0))", "(foo 0)");
    runTest(bothTregex, "(abc (foo 0))", "(foo 0)");
    runTest(bothTregex, "(bbb (foo 0))", "(foo 0)");
    runTest(bothTregex, "(bcd (foo 0))", "(foo 0)");
    runTest(bothTregex, "(ccc (foo 0))");
  }

  // tests for following/preceding described chains
  public void testPrecedesDescribedChain() {
    runTest("DT .+(JJ) NN", "(NP (DT the) (JJ large) (JJ green) (NN house))", "(DT the)");
    runTest("DT .+(@JJ) /^NN/", "(NP (PDT both) (DT the) (JJ-SIZE large) (JJ-COLOUR green) (NNS houses))", "(DT the)");
    runTest("NN ,+(JJ) DT", "(NP (DT the) (JJ large) (JJ green) (NN house))", "(NN house)");
    runTest("NNS ,+(@JJ) /^DT/", "(NP (PDT both) (DT the) (JJ-SIZE large) (JJ-COLOUR green) (NNS houses))", "(NNS houses)");
    runTest("NNS ,+(/^(JJ|DT).*$/) PDT", "(NP (PDT both) (DT the) (JJ-SIZE large) (JJ-COLOUR green) (NNS houses))", "(NNS houses)");
    runTest("NNS ,+(@JJ) JJ", "(NP (PDT both) (DT the) (JJ large) (JJ-COLOUR green) (NNS houses))", "(NNS houses)");
    // TODO: The patterns below should work but don't
    // runTest("DT .+(JJ) JJ", "(NP (DT the) (JJ large) (JJ green) (NN house))", "(DT the)");
    // runTest("NNS ,+(@JJ) /JJ/", "(NP (PDT both) (DT the) (JJ large) (JJ-COLOUR green) (NNS houses))", "(NNS houses)");
    // runTest("NNS ,+(@JJ) /^JJ$/", "(NP (PDT both) (DT the) (JJ large) (JJ-COLOUR green) (NNS houses))", "(NNS houses)");
    // runTest("NNS ,+(@JJ) @JJ", "(NP (PDT both) (DT the) (JJ large) (JJ-COLOUR green) (NNS houses))", "(NNS houses)");
  }

  // TODO: tests for patterns made with different headfinders,
  // which will verify the thread safety of using different headfinders

  public void testDominateDescribedChain() {
    runTest("foo <+(bar) baz", "(a (foo (baz)))", "(foo (baz))");
    runTest("foo <+(bar) baz", "(a (foo (bar (baz))))", "(foo (bar (baz)))");
    runTest("foo <+(bar) baz", "(a (foo (bar (bar (baz)))))",
            "(foo (bar (bar (baz))))");
    runTest("foo <+(bar) baz", "(a (foo (bif (baz))))");
    runTest("foo <+(!bif) baz", "(a (foo (bif (baz))))");
    runTest("foo <+(!bif) baz", "(a (foo (bar (baz))))", "(foo (bar (baz)))");
    runTest("foo <+(/b/) baz", "(a (foo (bif (baz))))", "(foo (bif (baz)))");
    runTest("foo <+(/b/) baz", "(a (foo (bar (bif (baz)))))",
            "(foo (bar (bif (baz))))");
    runTest("foo <+(bar) baz", "(a (foo (bar (blah 1) (bar (baz)))))",
            "(foo (bar (blah 1) (bar (baz))))");

    runTest("baz >+(bar) foo", "(a (foo (baz)))", "(baz)");
    runTest("baz >+(bar) foo", "(a (foo (bar (baz))))", "(baz)");
    runTest("baz >+(bar) foo", "(a (foo (bar (bar (baz)))))", "(baz)");
    runTest("baz >+(bar) foo", "(a (foo (bif (baz))))");
    runTest("baz >+(!bif) foo", "(a (foo (bif (baz))))");
    runTest("baz >+(!bif) foo", "(a (foo (bar (baz))))", "(baz)");
    runTest("baz >+(/b/) foo", "(a (foo (bif (baz))))", "(baz)");
    runTest("baz >+(/b/) foo", "(a (foo (bar (bif (baz)))))", "(baz)");
    runTest("baz >+(bar) foo", "(a (foo (bar (blah 1) (bar (baz)))))",
            "(baz)");
  }

  public void testSegmentedAndEqualsExpressions() {
    runTest("foo : bar", "(a (foo) (bar))", "(foo)");
    runTest("foo : bar", "(a (foo))");
    runTest("(foo << bar) : (foo << baz)", "(a (foo (bar 1)) (foo (baz 2)))",
            "(foo (bar 1))");
    runTest("(foo << bar) : (foo << baz)", "(a (foo (bar 1)) (foo (baz 2)))",
            "(foo (bar 1))");
    runTest("(foo << bar) == (foo << baz)", "(a (foo (bar)) (foo (baz)))");
    runTest("(foo << bar) : (foo << baz)", "(a (foo (bar) (baz)))",
            "(foo (bar) (baz))");
    runTest("(foo << bar) == (foo << baz)", "(a (foo (bar) (baz)))",
            "(foo (bar) (baz))");
    runTest("(foo << bar) : (baz >> a)", "(a (foo (bar) (baz)))",
            "(foo (bar) (baz))");
    runTest("(foo << bar) == (baz >> a)", "(a (foo (bar) (baz)))");

    runTest("foo == foo", "(a (foo (bar)))", "(foo (bar))");
    runTest("foo << bar == foo", "(a (foo (bar)) (foo (baz)))",
            "(foo (bar))");
    runTest("foo << bar == foo", "(a (foo (bar) (baz)))",
            "(foo (bar) (baz))");
    runTest("foo << bar == foo << baz", "(a (foo (bar) (baz)))",
            "(foo (bar) (baz))");
    runTest("foo << bar : (foo << baz)", "(a (foo (bar)) (foo (baz)))",
            "(foo (bar))");
  }

  public void testTwoChildren() {
    runTest("foo << bar << baz", "(foo (bar 1) (baz 2))",
            "(foo (bar 1) (baz 2))");
    // this is a poorly written pattern that will match 4 times
    runTest("foo << __ << baz", "(foo (bar 1) (baz 2))",
            "(foo (bar 1) (baz 2))", "(foo (bar 1) (baz 2))",
            "(foo (bar 1) (baz 2))", "(foo (bar 1) (baz 2))");
    // this one also matches 4 times
    runTest("foo << bar << __", "(foo (bar 1) (baz 2))",
            "(foo (bar 1) (baz 2))", "(foo (bar 1) (baz 2))",
            "(foo (bar 1) (baz 2))", "(foo (bar 1) (baz 2))");
    // this one also matches 4 times
    runTest("foo << __ << __", "(foo (bar 1))",
            "(foo (bar 1))", "(foo (bar 1))",
            "(foo (bar 1))", "(foo (bar 1))");
    // same thing, just making sure variable assignment doesn't throw
    // it off
    runTest("foo << __=a << __=b", "(foo (bar 1))",
            "(foo (bar 1))", "(foo (bar 1))",
            "(foo (bar 1))", "(foo (bar 1))");
    // 16 times!  hopefully no one writes patterns like this
    runTest("foo << __ << __", "(foo (bar 1) (baz 2))",
            "(foo (bar 1) (baz 2))", "(foo (bar 1) (baz 2))",
            "(foo (bar 1) (baz 2))", "(foo (bar 1) (baz 2))",
            "(foo (bar 1) (baz 2))", "(foo (bar 1) (baz 2))",
            "(foo (bar 1) (baz 2))", "(foo (bar 1) (baz 2))",
            "(foo (bar 1) (baz 2))", "(foo (bar 1) (baz 2))",
            "(foo (bar 1) (baz 2))", "(foo (bar 1) (baz 2))",
            "(foo (bar 1) (baz 2))", "(foo (bar 1) (baz 2))",
            "(foo (bar 1) (baz 2))", "(foo (bar 1) (baz 2))");
    // note: this matches because we set a=(bar 1), b=(1)
    runTest("(foo << __=a << __=b) : (=a !== =b)", "(foo (bar 1))",
            "(foo (bar 1))", "(foo (bar 1))");
    runTest("(foo < __=a < __=b) : (=a !== =b)", "(foo (bar 1))");
    // 12 times: 16 possible ways to match the nodes, but 4 of them
    // are ruled out because they are the same node matching twice
    runTest("(foo << __=a << __=b) : (=a !== =b)",
            "(foo (bar 1) (baz 2))",
            "(foo (bar 1) (baz 2))", "(foo (bar 1) (baz 2))",
            "(foo (bar 1) (baz 2))", "(foo (bar 1) (baz 2))",
            "(foo (bar 1) (baz 2))", "(foo (bar 1) (baz 2))",
            "(foo (bar 1) (baz 2))", "(foo (bar 1) (baz 2))",
            "(foo (bar 1) (baz 2))", "(foo (bar 1) (baz 2))",
            "(foo (bar 1) (baz 2))", "(foo (bar 1) (baz 2))");
    // would need three unique descendants, but we only have two, so
    // this pattern doesn't match anything
    runTest("(foo << __=a << __=b << __=c) : " +
            "(=a !== =b) : (=a !== =c) : (=b !== =c)",
            "(foo (bar 1))");
    // TODO: this should work, but it doesn't even parse
    //runTest("foo << __=a << __=b : !(=a == =b)", "(foo (bar 1))");
  }

  public void testDocExamples() {
    runTest("S < VP < NP", "(S (VP) (NP))", "(S (VP) (NP))");
    runTest("S < VP < NP", "(a (S (VP) (NP)) (S (NP) (VP)))",
            "(S (VP) (NP))", "(S (NP) (VP))");
    runTest("S < VP < NP", "(S (VP (NP)))");
    runTest("S < VP & < NP", "(S (VP) (NP))", "(S (VP) (NP))");
    runTest("S < VP & < NP", "(a (S (VP) (NP)) (S (NP) (VP)))",
            "(S (VP) (NP))", "(S (NP) (VP))");
    runTest("S < VP & < NP", "(S (VP (NP)))");
    runTest("S < VP << NP", "(S (VP (NP)))", "(S (VP (NP)))");
    runTest("S < VP << NP", "(S (VP) (foo NP))", "(S (VP) (foo NP))");
    runTest("S < (VP < NP)", "(S (VP (NP)))", "(S (VP (NP)))");
    runTest("S < (NP $++ VP)", "(S (NP) (VP))", "(S (NP) (VP))");
    runTest("S < (NP $++ VP)", "(S (NP VP))");

    runTest("(NP < NN | < NNS)", "((NP NN) (NP foo) (NP NNS))",
            "(NP NN)", "(NP NNS)");
    runTest("(NP (< NN | < NNS) & > S)",
            "(foo (S (NP NN) (NP foo) (NP NNS)) (NP NNS))",
            "(NP NN)", "(NP NNS)");
    runTest("(NP [< NN | < NNS] & > S)",
            "(foo (S (NP NN) (NP foo) (NP NNS)) (NP NNS))",
            "(NP NN)", "(NP NNS)");
  }

  /**
   * An example from our code which looks for month-day-year patterns
   * in PTB.  Relies on the pattern splitting and variable matching
   * features.
   */
  public void testMonthDayYear() {
    String MONTH_REGEX = "January|February|March|April|May|June|July|August|September|October|November|December|Jan\\.|Feb\\.|Mar\\.|Apr\\.|Aug\\.|Sep\\.|Sept\\.|Oct\\.|Nov\\.|Dec\\.";
    String testPattern = "NP=root <1 (NP=monthdayroot <1 (NNP=month <: /" + MONTH_REGEX +"/) <2 (CD=day <: __)) <2 (/^,$/=comma <: /^,$/) <3 (NP=yearroot <: (CD=year <: __)) : (=root <- =yearroot) : (=monthdayroot <- =day)";

    runTest(testPattern, "(ROOT (S (NP (NNP Mr.) (NNP Good)) (VP (VBZ devotes) (NP (RB much) (JJ serious) (NN space)) (PP (TO to) (NP (NP (DT the) (NNS events)) (PP (IN of) (NP (NP (NP (NNP Feb.) (CD 25)) (, ,) (NP (CD 1942))) (, ,) (SBAR (WHADVP (WRB when)) (S (NP (JJ American) (NNS gunners)) (VP (VBD spotted) (NP (NP (JJ strange) (NNS lights)) (PP (IN in) (NP (NP (DT the) (NN sky)) (PP (IN above) (NP (NNP Los) (NNP Angeles)))))))))))))) (. .)))", "(NP (NP (NNP Feb.) (CD 25)) (, ,) (NP (CD 1942)))");
    runTest(testPattern, "(ROOT (S (NP (DT The) (JJ preferred) (NNS shares)) (VP (MD will) (VP (VB carry) (NP (NP (DT a) (JJ floating) (JJ annual) (NN dividend)) (ADJP (JJ equal) (PP (TO to) (NP (NP (CD 72) (NN %)) (PP (IN of) (NP (NP (DT the) (JJ 30-day) (NNS bankers) (POS ')) (NN acceptance) (NN rate))))))) (PP (IN until) (NP (NP (NNP Dec.) (CD 31)) (, ,) (NP (CD 1994)))))) (. .)))", "(NP (NP (NNP Dec.) (CD 31)) (, ,) (NP (CD 1994)))");
    runTest(testPattern, "(ROOT (S (NP (PRP It)) (VP (VBD said) (SBAR (S (NP (NN debt)) (VP (VBD remained) (PP (IN at) (NP (NP (DT the) (QP ($ $) (CD 1.22) (CD billion))) (SBAR (WHNP (DT that)) (S (VP (VBZ has) (VP (VBD prevailed) (PP (IN since) (NP (JJ early) (CD 1989))))))))) (, ,) (SBAR (IN although) (S (NP (IN that)) (VP (VBN compared) (PP (IN with) (NP (NP (QP ($ $) (CD 911) (CD million))) (PP (IN at) (NP (NP (NNP Sept.) (CD 30)) (, ,) (NP (CD 1988))))))))))))) (. .)))", "(NP (NP (NNP Sept.) (CD 30)) (, ,) (NP (CD 1988)))");
    runTest(testPattern, "(ROOT (S (NP (DT The) (JJ new) (NNS notes)) (VP (MD will) (VP (VB bear) (NP (NN interest)) (PP (PP (IN at) (NP (NP (CD 5.5) (NN %)) (PP (IN through) (NP (NP (NNP July) (CD 31)) (, ,) (NP (CD 1991)))))) (, ,) (CC and) (ADVP (RB thereafter)) (PP (IN at) (NP (CD 10) (NN %)))))) (. .)))", "(NP (NP (NNP July) (CD 31)) (, ,) (NP (CD 1991)))");
    runTest(testPattern, "(ROOT (S (NP (NP (NNP Francis) (NNP M.) (NNP Wheat)) (, ,) (NP (NP (DT a) (JJ former) (NNPS Securities)) (CC and) (NP (NNP Exchange) (NNP Commission) (NN member))) (, ,)) (VP (VBD headed) (NP (NP (DT the) (NN panel)) (SBAR (WHNP (WDT that)) (S (VP (VBD had) (VP (VP (VBN studied) (NP (DT the) (NNS issues)) (PP (IN for) (NP (DT a) (NN year)))) (CC and) (VP (VBD proposed) (NP (DT the) (NNP FASB)) (PP (IN on) (NP (NP (NNP March) (CD 30)) (, ,) (NP (CD 1972))))))))))) (. .)))", "(NP (NP (NNP March) (CD 30)) (, ,) (NP (CD 1972)))");
    runTest(testPattern, "(ROOT (S (NP (DT The) (NNP FASB)) (VP (VBD had) (NP (PRP$ its) (JJ initial) (NN meeting)) (PP (IN on) (NP (NP (NNP March) (CD 28)) (, ,) (NP (CD 1973))))) (. .)))", "(NP (NP (NNP March) (CD 28)) (, ,) (NP (CD 1973)))");
    runTest(testPattern, "(ROOT (S (S (PP (IN On) (NP (NP (NNP Dec.) (CD 13)) (, ,) (NP (CD 1973)))) (, ,) (NP (PRP it)) (VP (VBD issued) (NP (PRP$ its) (JJ first) (NN rule)))) (: ;) (S (NP (PRP it)) (VP (VBD required) (S (NP (NNS companies)) (VP (TO to) (VP (VB disclose) (NP (NP (JJ foreign) (NN currency) (NNS translations)) (PP (IN in) (NP (NNP U.S.) (NNS dollars))))))))) (. .)))", "(NP (NP (NNP Dec.) (CD 13)) (, ,) (NP (CD 1973)))");
    runTest(testPattern, "(ROOT (S (NP (NP (NNP Fidelity) (NNPS Investments)) (, ,) (NP (NP (DT the) (NN nation) (POS 's)) (JJS largest) (NN fund) (NN company)) (, ,)) (VP (VBD said) (SBAR (S (NP (NN phone) (NN volume)) (VP (VBD was) (NP (NP (QP (RBR more) (IN than) (JJ double)) (PRP$ its) (JJ typical) (NN level)) (, ,) (CC but) (ADVP (RB still)) (NP (NP (NN half) (DT that)) (PP (IN of) (NP (NP (NNP Oct.) (CD 19)) (, ,) (NP (CD 1987)))))))))) (. .)))", "(NP (NP (NNP Oct.) (CD 19)) (, ,) (NP (CD 1987)))");
    runTest(testPattern, "(ROOT (S (NP (JJ SOFT) (NN CONTACT) (NNS LENSES)) (VP (VP (VBP WON) (NP (JJ federal) (NN blessing)) (PP (IN on) (NP (NP (NNP March) (CD 18)) (, ,) (NP (CD 1971))))) (, ,) (CC and) (VP (ADVP (RB quickly)) (VBD became) (NP (NN eye) (NNS openers)) (PP (IN for) (NP (PRP$ their) (NNS makers))))) (. .)))", "(NP (NP (NNP March) (CD 18)) (, ,) (NP (CD 1971)))");
    runTest(testPattern, "(ROOT (NP (NP (NP (VBN Annualized) (NN interest) (NNS rates)) (PP (IN on) (NP (JJ certain) (NNS investments))) (SBAR (IN as) (S (VP (VBN reported) (PP (IN by) (NP (DT the) (NNP Federal) (NNP Reserve) (NNP Board))) (PP (IN on) (NP (DT a) (JJ weekly-average) (NN basis))))))) (: :) (NP-TMP (NP (CD 1989)) (CC and) (NP (NP (NNP Wednesday)) (NP (NP (NNP October) (CD 4)) (, ,) (NP (CD 1989))))) (. .)))", "(NP (NP (NNP October) (CD 4)) (, ,) (NP (CD 1989)))");
    runTest(testPattern, "(ROOT (S (S (ADVP (RB Together))) (, ,) (NP (DT the) (CD two) (NNS stocks)) (VP (VP (VBD wreaked) (NP (NN havoc)) (PP (IN among) (NP (NN takeover) (NN stock) (NNS traders)))) (, ,) (CC and) (VP (VBD caused) (NP (NP (DT a) (ADJP (CD 7.3) (NN %)) (NN drop)) (PP (IN in) (NP (DT the) (NNP Dow) (NNP Jones) (NNP Transportation) (NNP Average))) (, ,) (ADJP (JJ second) (PP (IN in) (NP (NN size))) (PP (RB only) (TO to) (NP (NP (DT the) (NN stock-market) (NN crash)) (PP (IN of) (NP (NP (NNP Oct.) (CD 19)) (, ,) (NP (CD 1987)))))))))) (. .)))", "(NP (NP (NNP Oct.) (CD 19)) (, ,) (NP (CD 1987)))");
  }

  /**
   * More complex tests, often based on examples from our source code
   */
  public void testComplex() {
    String testPattern = "S < (NP=m1 $.. (VP < ((/VB/ < /^(am|are|is|was|were|'m|'re|'s|be)$/) $.. NP=m2)))";
    String testTree = "(S (NP (NP (DT The) (JJ next) (NN stop)) (PP (IN on) (NP (DT the) (NN itinerary)))) (VP (VBD was) (NP (NP (NNP Chad)) (, ,) (SBAR (WHADVP (WRB where)) (S (NP (NNP Chen)) (VP (VBD dined) (PP (IN with) (NP (NP (NNP Chad) (POS \'s)) (NNP President) (NNP Idris) (NNP Debi)))))))) (. .))";
    runTest(testPattern, "(ROOT " + testTree + ")", testTree);

    testTree = "(S (NP (NNP Chen) (NNP Shui) (HYPH -) (NNP bian)) (VP (VBZ is) (NP (NP (DT the) (JJ first) (NML (NNP ROC) (NN president))) (SBAR (S (ADVP (RB ever)) (VP (TO to) (VP (VB travel) (PP (IN to) (NP (JJ western) (NNP Africa))))))))) (. .))";
    runTest(testPattern, "(ROOT " + testTree + ")", testTree);

    testTree = "(ROOT (S (NP (PRP$ My) (NN dog)) (VP (VBZ is) (VP (VBG eating) (NP (DT a) (NN sausage)))) (. .)))";
    runTest(testPattern, testTree);

    testTree = "(ROOT (S (NP (PRP He)) (VP (MD will) (VP (VB be) (ADVP (RB here) (RB soon)))) (. .)))";
    runTest(testPattern, testTree);

    testPattern = "/^NP(?:-TMP|-ADV)?$/=m1 < (NP=m2 $- /^,$/ $-- NP=m3 !$ CC|CONJP)";
    testTree = "(ROOT (S (NP (NP (NP (NP (DT The) (NNP ROC) (POS \'s)) (NN ambassador)) (PP (IN to) (NP (NNP Nicaragua)))) (, ,) (NP (NNP Antonio) (NNP Tsai)) (, ,)) (ADVP (RB bluntly)) (VP (VBD argued) (PP (IN in) (NP (NP (DT a) (NN briefing)) (PP (IN with) (NP (NNP Chen))))) (SBAR (IN that) (S (NP (NP (NP (NNP Taiwan) (POS \'s)) (JJ foreign) (NN assistance)) (PP (IN to) (NP (NNP Nicaragua)))) (VP (VBD was) (VP (VBG being) (ADJP (JJ misused))))))) (. .)))";
    String expectedResult = "(NP (NP (NP (NP (DT The) (NNP ROC) (POS 's)) (NN ambassador)) (PP (IN to) (NP (NNP Nicaragua)))) (, ,) (NP (NNP Antonio) (NNP Tsai)) (, ,))";
    runTest(testPattern, testTree, expectedResult);

    testTree = "(ROOT (S (PP (IN In) (NP (NP (DT the) (NN opinion)) (PP (IN of) (NP (NP (NNP Norman) (NNP Hsu)) (, ,) (NP (NP (NN vice) (NN president)) (PP (IN of) (NP (NP (DT a) (NNS foods) (NN company)) (SBAR (WHNP (WHNP (WP$ whose) (NN family)) (PP (IN of) (NP (CD four)))) (S (VP (VBD had) (VP (VBN spent) (NP (QP (DT a) (JJ few)) (NNS years)) (PP (IN in) (NP (NNP New) (NNP Zealand))) (PP (IN before) (S (VP (VBG moving) (PP (IN to) (NP (NNP Dongguan))))))))))))))))) (, ,) (`` \") (NP (NP (DT The) (JJ first) (NN thing)) (VP (TO to) (VP (VB do)))) (VP (VBZ is) (S (VP (VB ask) (NP (DT the) (NNS children)) (NP (PRP$ their) (NN reason)) (PP (IN for) (S (VP (VBG saying) (NP (JJ such) (NNS things)))))))) (. .)))";
    expectedResult = "(NP (NP (NNP Norman) (NNP Hsu)) (, ,) (NP (NP (NN vice) (NN president)) (PP (IN of) (NP (NP (DT a) (NNS foods) (NN company)) (SBAR (WHNP (WHNP (WP$ whose) (NN family)) (PP (IN of) (NP (CD four)))) (S (VP (VBD had) (VP (VBN spent) (NP (QP (DT a) (JJ few)) (NNS years)) (PP (IN in) (NP (NNP New) (NNP Zealand))) (PP (IN before) (S (VP (VBG moving) (PP (IN to) (NP (NNP Dongguan))))))))))))))";
    runTest(testPattern, testTree, expectedResult);

    testTree = "(ROOT (S (NP (NP (NNP Banana)) (, ,) (NP (NN orange)) (, ,) (CC and) (NP (NN apple))) (VP (VBP are) (NP (NNS fruits))) (. .)))";
    runTest(testPattern, testTree);

    testTree = "(ROOT (S (NP (PRP He)) (, ,) (ADVP (RB however)) (, ,) (VP (VBZ does) (RB not) (VP (VB look) (ADJP (JJ fine)))) (. .)))";
    runTest(testPattern, testTree);
  }

  /**
   * More complex patterns to test
   */
  public void testComplex2() {
    String[] inputTrees = {"(ROOT (S (NP (PRP You)) (VP (VBD did) (VP (VB go) (WHADVP (WRB How) (JJ long)) (PP (IN for)))) (. .)))",
                           "(ROOT (S (NP (NNS Raccoons)) (VP (VBP do) (VP (VB come) (WHADVP (WRB When)) (PRT (RP out)))) (. .)))",
                           "(ROOT (S (NP (PRP She)) (VP (VBZ is) (VP (WHADVP (WRB Where)) (VBG working))) (. .)))",
                           "(ROOT (S (NP (PRP You)) (VP (VBD did) (VP (WHNP (WP What)) (VB do))) (. .)))",
                           "(ROOT (S (NP (PRP You)) (VP (VBD did) (VP (VB do) (PP (IN in) (NP (NNP Australia))) (WHNP (WP What)))) (. .)))"};

    String pattern = "WHADVP=whadvp > VP $+ /[A-Z]*/=last ![$++ (PP < NP)]";
    runTest(pattern, inputTrees[0], "(WHADVP (WRB How) (JJ long))");
    runTest(pattern, inputTrees[1], "(WHADVP (WRB When))");
    runTest(pattern, inputTrees[2], "(WHADVP (WRB Where))");
    runTest(pattern, inputTrees[3]);
    runTest(pattern, inputTrees[4]);

    pattern = "VP < (/^WH/=wh $++ /^VB/=vb)";
    runTest(pattern, inputTrees[0]);
    runTest(pattern, inputTrees[1]);
    runTest(pattern, inputTrees[2], "(VP (WHADVP (WRB Where)) (VBG working))");
    runTest(pattern, inputTrees[3], "(VP (WHNP (WP What)) (VB do))");
    runTest(pattern, inputTrees[4]);

    pattern = "PP=pp > VP $+ WHNP=whnp";
    runTest(pattern, inputTrees[0]);
    runTest(pattern, inputTrees[1]);
    runTest(pattern, inputTrees[2]);
    runTest(pattern, inputTrees[3]);
    runTest(pattern, inputTrees[4], "(PP (IN in) (NP (NNP Australia)))");
  }

  public void testNamed() {
    Tree tree = treeFromString("(a (foo 1) (bar 2) (bar 3))");
    TregexPattern pattern = TregexPattern.compile("foo=a $ bar=b");
    TregexMatcher matcher = pattern.matcher(tree);
    assertTrue(matcher.find());
    assertEquals("(foo 1)", matcher.getMatch().toString());
    assertEquals("(foo 1)", matcher.getNode("a").toString());
    assertEquals("(bar 2)", matcher.getNode("b").toString());
    assertTrue(matcher.find());
    assertEquals("(foo 1)", matcher.getMatch().toString());
    assertEquals("(foo 1)", matcher.getNode("a").toString());
    assertEquals("(bar 3)", matcher.getNode("b").toString());
    assertFalse(matcher.find());
  }

  public void testLink() {
    // matched node will be (bar 3), the next bar matches (bar 2), and
    // the foo at the end obviously matches the (foo 1)
    runTest("bar $- (bar $- foo)", "(a (foo 1) (bar 2) (bar 3))", "(bar 3)");
    // same thing, but this tests the link functionality, as the
    // second match should also be (bar 2)
    runTest("bar=a $- (~a $- foo)", "(a (foo 1) (bar 2) (bar 3))", "(bar 3)");
    // won't work, since (bar 3) doesn't satisfy the next-to-foo
    // relation, and (bar 2) isn't the same node as (bar 3)
    runTest("bar=a $- (=a $- foo)", "(a (foo 1) (bar 2) (bar 3))");
    // links can be saved as named nodes as well, so this should work
    runTest("bar=a $- (~a=b $- foo)", "(a (foo 1) (bar 2) (bar 3))", "(bar 3)");

    // run a few of the same tests, but this time dissect the results
    // to make sure the captured nodes are the correct nodes
    Tree tree = treeFromString("(a (foo 1) (bar 2) (bar 3))");
    TregexPattern pattern = TregexPattern.compile("bar=a $- (~a $- foo)");
    TregexMatcher matcher = pattern.matcher(tree);
    assertTrue(matcher.find());
    assertEquals("(bar 3)", matcher.getMatch().toString());
    assertEquals("(bar 3)", matcher.getNode("a").toString());
    assertFalse(matcher.find());

    tree = treeFromString("(a (foo 1) (bar 2) (bar 3))");
    pattern = TregexPattern.compile("bar=a $- (~a=b $- foo)");
    matcher = pattern.matcher(tree);
    assertTrue(matcher.find());
    assertEquals("(bar 3)", matcher.getMatch().toString());
    assertEquals("(bar 3)", matcher.getNode("a").toString());
    assertEquals("(bar 2)", matcher.getNode("b").toString());
    assertFalse(matcher.find());

    tree = treeFromString("(a (foo 1) (bar 2) (bar 3))");
    pattern = TregexPattern.compile("bar=a $- (~a=b $- foo=c)");
    matcher = pattern.matcher(tree);
    assertTrue(matcher.find());
    assertEquals("(bar 3)", matcher.getMatch().toString());
    assertEquals("(bar 3)", matcher.getNode("a").toString());
    assertEquals("(bar 2)", matcher.getNode("b").toString());
    assertEquals("(foo 1)", matcher.getNode("c").toString());
    assertFalse(matcher.find());
  }

  /** Test another variant of using links, this time with pattern partitions */
  public void testBackref() {
    TregexPattern tregex = TregexPattern.compile("__ <1 B=n <2 ~n");
    Tree tree = treeFromString("(A (B w) (B x))");
    TregexMatcher matcher = tregex.matcher(tree);
    assertTrue(matcher.find());
    Tree match = matcher.getMatch();
    assertEquals("(A (B w) (B x))", match.toString());
    Tree node = matcher.getNode("n");
    assertEquals("(B w)", node.toString());
    assertFalse(matcher.find());

    tregex = TregexPattern.compile("__ < B=n <2 B=m : (=n !== =m)");
    tree = treeFromString("(A (B w) (B x))");
    matcher = tregex.matcher(tree);
    assertTrue(matcher.find());
    match = matcher.getMatch();
    assertEquals("(A (B w) (B x))", match.toString());
    node = matcher.getNode("n");
    assertEquals("(B w)", node.toString());
    assertFalse(matcher.find());
  }

  public void testNonsense() {
    // can't name a variable twice
    try {
      TregexPattern pattern = TregexPattern.compile("foo=a $ bar=a");
      throw new RuntimeException("Expected a parse exception");
    } catch (TregexParseException e) {
      // yay, passed
    }

    // another way of doing the same thing
    try {
      TregexPattern pattern = TregexPattern.compile("foo=a > bar=b $ ~a=b");
      throw new RuntimeException("Expected a parse exception");
    } catch (TregexParseException e) {
      // yay, passed
    }
    // ... but this should work
    TregexPattern.compile("foo=a > bar=b $ ~a");

    // can't link to a variable that doesn't exist yet
    try {
      TregexPattern pattern = TregexPattern.compile("~a $- (bar=a $- foo)");
      throw new RuntimeException("Expected a parse exception");
    } catch (TregexParseException e) {
      // yay, passed
    }

    // can't reference a variable that doesn't exist yet
    try {
      TregexPattern pattern = TregexPattern.compile("=a $- (bar=a $- foo)");
      throw new RuntimeException("Expected a parse exception");
    } catch (TregexParseException e) {
      // yay, passed
    }

    // you'd have to be really demented to do this
    try {
      TregexPattern pattern = TregexPattern.compile("~a=a $- (bar=b $- foo)");
      throw new RuntimeException("Expected a parse exception");
    } catch (TregexParseException e) {
      // yay, passed
    }

    // This should work... no reason this would barf
    TregexPattern.compile("foo=a : ~a");
    TregexPattern.compile("a < foo=a | < bar=a");

    // can't have a link in one part of a disjunction to a variable in
    // another part of the disjunction; it won't be set if you get to
    // the ~a part, after all
    try {
      TregexPattern.compile("a < foo=a | < ~a");
      throw new RuntimeException("Expected a parse exception");
    } catch (TregexParseException e) {
      // yay, passed
    }

    // same, but for references
    try {
      TregexPattern.compile("a < foo=a | < =a");
      throw new RuntimeException("Expected a parse exception");
    } catch (TregexParseException e) {
      // yay, passed
    }

    // can't name a variable under a negation
    try {
      TregexPattern pattern = TregexPattern.compile("__ ! > __=a");
      throw new RuntimeException("Expected a parse exception");
    } catch (TregexParseException e) {
      // yay, passed
    }
  }

  public void testNumberedSister() {
    // this shouldn't mean anything
    try {
      TregexPattern pattern = TregexPattern.compile("A $5 B");
    } catch (TregexParseException e) {
      // yay, passed
    }

    // this should be fine
    TregexPattern pattern = TregexPattern.compile("A <5 B");
  }

  public void testAncestorOfLeaf() {
    runTest("A <<< b", "(ROOT (A (B b)))", "(A (B b))");
    runTest("A <<< b", "(ROOT (A (B c)))");
    runTest("A <<< b", "(ROOT (A (B b) (C b)))", "(A (B b) (C b))", "(A (B b) (C b))");
    runTest("A <<< b", "(ROOT (A (B z) (C b)))", "(A (B z) (C b))");
    runTest("A <<< __", "(ROOT (A (B z) (C b)))", "(A (B z) (C b))", "(A (B z) (C b))");
    runTest("A <<< __", "(ROOT (A (B b)) (A (C c)))", "(A (B b))", "(A (C c))");
    runTest("A <<< __", "(ROOT (A (B b)) (B (C c)))", "(A (B b))");
    runTest("A <<< (b . c=foo) <<< =foo", "(ROOT (A (B b) (C c)))", "(A (B b) (C c))");
  }

  public void testAncestorOfIthLeaf() {
    runTest("A <<<1 b", "(ROOT (A (B b)))", "(A (B b))");
    runTest("A <<<2 b", "(ROOT (A (B b)))");
    runTest("A <<<-1 b", "(ROOT (A (B b)))", "(A (B b))");
    runTest("A <<<1 b", "(ROOT (A (B z) (C b)))");
    runTest("A <<<2 b", "(ROOT (A (B z) (C b)))", "(A (B z) (C b))");
    runTest("A <<<-1 b", "(ROOT (A (B z) (C b)))", "(A (B z) (C b))");
    runTest("A <<<-2 b", "(ROOT (A (B z) (C b)))");
    runTest("A <<<-1 z", "(ROOT (A (B z) (C b)))");
    runTest("A <<<-2 z", "(ROOT (A (B z) (C b)))", "(A (B z) (C b))");
  }

  /** test the _ROOT_ node description */
  public void testRootDescription() {
    runTest("_ROOT_", "(ROOT (A apple))", "(ROOT (A apple))");
    runTest("A > _ROOT_", "(ROOT (A apple))", "(A apple)");
    runTest("A > _ROOT_", "(ROOT (A apple) (B (A aardvark)))", "(A apple)");
    runTest("A !> _ROOT_", "(ROOT (A apple) (B (A aardvark)))", "(A aardvark)");
    runTest("_ROOT_ <<<2 b", "(ROOT (A (B z) (C b)))", "(ROOT (A (B z) (C b)))");
  }

  public void testHeadOfPhrase() {
    runTest("NP <# NNS", "(NP (NN work) (NNS practices))", "(NP (NN work) (NNS practices))");
    runTest("NP <# NN", "(NP (NN work) (NNS practices))"); // should have no results
    runTest("NP <<# NNS",
            "(NP (NP (NN work) (NNS practices)) (PP (IN in) (NP (DT the) (JJ former) (NNP Soviet) (NNP Union))))",
            "(NP (NP (NN work) (NNS practices)) (PP (IN in) (NP (DT the) (JJ former) (NNP Soviet) (NNP Union))))",
            "(NP (NN work) (NNS practices))");
    runTest("NP !<# NNS <<# NNS",
            "(NP (NP (NN work) (NNS practices)) (PP (IN in) (NP (DT the) (JJ former) (NNP Soviet) (NNP Union))))",
            "(NP (NP (NN work) (NNS practices)) (PP (IN in) (NP (DT the) (JJ former) (NNP Soviet) (NNP Union))))");
    runTest("NP !<# NNP <<# NNP",
            "(NP (NP (NN work) (NNS practices)) (PP (IN in) (NP (DT the) (JJ former) (NNP Soviet) (NNP Union))))"); // no results
    runTest("NNS ># NP",
            "(NP (NP (NN work) (NNS practices)) (PP (IN in) (NP (DT the) (JJ former) (NNP Soviet) (NNP Union))))",
            "(NNS practices)");
    runTest("NNS ># (NP < PP)",
            "(NP (NP (NN work) (NNS practices)) (PP (IN in) (NP (DT the) (JJ former) (NNP Soviet) (NNP Union))))"); // no results
    runTest("NNS >># (NP < PP)",
            "(NP (NP (NN work) (NNS practices)) (PP (IN in) (NP (DT the) (JJ former) (NNP Soviet) (NNP Union))))",
            "(NNS practices)");
    runTest("NP <<# /^NN/",
            "(NP (NP (NN work) (NNS practices)) (PP (IN in) (NP (DT the) (JJ former) (NNP Soviet) (NNP Union))))",
            "(NP (NP (NN work) (NNS practices)) (PP (IN in) (NP (DT the) (JJ former) (NNP Soviet) (NNP Union))))",
            "(NP (NN work) (NNS practices))",
            "(NP (DT the) (JJ former) (NNP Soviet) (NNP Union)))");
  }

  public void testOnlyMatchRoot() {
    String treeString = "(a (foo 1) (bar 2))";
    Tree tree = treeFromString(treeString);
    TregexPattern pattern = TregexPattern.compile("__=a ! > __");
    TregexMatcher matcher = pattern.matcher(tree);

    assertTrue(matcher.find());
    assertEquals(treeString, matcher.getMatch().toString());
    assertEquals(treeString, matcher.getNode("a").toString());
    assertFalse(matcher.find());
  }

  public void testRepeatedVariables() {
    Tree tree = treeFromString("(root (a (foo 1)) (a (bar 2)))");
    TregexPattern pattern = TregexPattern.compile("a < foo=a | < bar=a");

    TregexMatcher matcher = pattern.matcher(tree);

    assertTrue(matcher.find());
    assertEquals("(a (foo 1))", matcher.getMatch().toString());
    assertEquals("(foo 1)", matcher.getNode("a").toString());

    assertTrue(matcher.find());
    assertEquals("(a (bar 2))", matcher.getMatch().toString());
    assertEquals("(bar 2)", matcher.getNode("a").toString());

    assertFalse(matcher.find());
  }

  /**
   * A test case provided by a user which leverages variable names.
   * Goal is to match this tree: <br>
   * (T
   *   (X
   *     (N
   *       (N Moe
   *         (PNT ,))))
   *   (NP
   *     (X
   *       (N Curly))
   *     (NP
   *       (CONJ and)
   *       (X
   *         (N Larry)))))
   */
  public void testMoeCurlyLarry() {
    String testString = ("(T (X (N (N Moe (PNT ,)))) (NP (X (N Curly)) " +
                         "(NP (CONJ and) (X (N Larry)))))");
    Tree tree = treeFromString(testString);
    TregexPattern pattern = TregexPattern.compile("PNT=p >>- (__=l >, (__=t <- (__=r <, __=m <- (__ <, CONJ <- __=z))))");
    TregexMatcher matcher = pattern.matcher(tree);
    assertTrue(matcher.find());
    assertEquals("(PNT ,)", matcher.getMatch().toString());
    assertEquals("(PNT ,)", matcher.getNode("p").toString());
    assertEquals("(X (N (N Moe (PNT ,))))", matcher.getNode("l").toString());
    assertEquals(testString, matcher.getNode("t").toString());
    assertEquals("(NP (X (N Curly)) (NP (CONJ and) (X (N Larry))))",
                 matcher.getNode("r").toString());
    assertEquals("(X (N Curly))", matcher.getNode("m").toString());
    assertEquals("(X (N Larry))", matcher.getNode("z").toString());
    assertFalse(matcher.find());


    pattern = TregexPattern.compile("PNT=p >>- (/(.+)/#1%var=l >, (__=t <- (__=r <, /(.+)/#1%var=m <- (__ <, CONJ <- /(.+)/#1%var=z))))");
    matcher = pattern.matcher(tree);
    assertTrue(matcher.find());
    assertEquals("(PNT ,)", matcher.getMatch().toString());
    assertEquals("(PNT ,)", matcher.getNode("p").toString());
    assertEquals("(X (N (N Moe (PNT ,))))", matcher.getNode("l").toString());
    assertEquals(testString, matcher.getNode("t").toString());
    assertEquals("(NP (X (N Curly)) (NP (CONJ and) (X (N Larry))))",
                 matcher.getNode("r").toString());
    assertEquals("(X (N Curly))", matcher.getNode("m").toString());
    assertEquals("(X (N Larry))", matcher.getNode("z").toString());
    assertFalse(matcher.find());


    pattern = TregexPattern.compile("PNT=p >>- (__=l >, (__=t <- (__=r <, ~l <- (__ <, CONJ <- ~l))))");
    matcher = pattern.matcher(tree);
    assertTrue(matcher.find());
    assertEquals("(PNT ,)", matcher.getMatch().toString());
    assertEquals("(PNT ,)", matcher.getNode("p").toString());
    assertEquals("(X (N (N Moe (PNT ,))))", matcher.getNode("l").toString());
    assertEquals(testString, matcher.getNode("t").toString());
    assertEquals("(NP (X (N Curly)) (NP (CONJ and) (X (N Larry))))",
                 matcher.getNode("r").toString());
    assertFalse(matcher.find());


    pattern = TregexPattern.compile("PNT=p >>- (__=l >, (__=t <- (__=r <, ~l=m <- (__ <, CONJ <- ~l=z))))");
    matcher = pattern.matcher(tree);
    assertTrue(matcher.find());
    assertEquals("(PNT ,)", matcher.getMatch().toString());
    assertEquals("(PNT ,)", matcher.getNode("p").toString());
    assertEquals("(X (N (N Moe (PNT ,))))", matcher.getNode("l").toString());
    assertEquals(testString, matcher.getNode("t").toString());
    assertEquals("(NP (X (N Curly)) (NP (CONJ and) (X (N Larry))))",
                 matcher.getNode("r").toString());
    assertEquals("(X (N Curly))", matcher.getNode("m").toString());
    assertEquals("(X (N Larry))", matcher.getNode("z").toString());
    assertFalse(matcher.find());
  }

  /**
   * Test a pattern with chinese characters in it, just to make sure
   * that also works
   */
  public void testChinese() {
    TregexPattern pattern = TregexPattern.compile("DEG|DEC < 的");
    runTest("DEG|DEC < 的", "(DEG (的 1))", "(DEG (的 1))");
  }

  /**
   * Add a few more tests for immediate sister to make sure that $+
   * doesn't accidentally match things that aren't non-immediate
   * sisters, which should only be matched by $++
   */
  public void testImmediateSister() {
    runTest("@NP < (/^,/=comma $+ CC)", "((NP NP , NP , NP , CC NP))", "(NP NP , NP , NP , CC NP)");
    runTest("@NP < (/^,/=comma $++ CC)", "((NP NP , NP , NP , CC NP))",
            "(NP NP , NP , NP , CC NP)", "(NP NP , NP , NP , CC NP)", "(NP NP , NP , NP , CC NP)");
    runTest("@NP < (@/^,/=comma $+ @CC)", "((NP NP , NP , NP , CC NP))", "(NP NP , NP , NP , CC NP)");

    TregexPattern pattern = TregexPattern.compile("@NP < (/^,/=comma $+ CC)");

    String treeString = "(NP NP (, 1) NP (, 2) NP (, 3) CC NP)";
    Tree tree = treeFromString(treeString);
    TregexMatcher matcher = pattern.matcher(tree);
    assertTrue(matcher.find());
    assertEquals(treeString, matcher.getMatch().toString());
    assertEquals("(, 3)", matcher.getNode("comma").toString());
    assertFalse(matcher.find());

    treeString = "(NP NP , NP , NP , CC NP)";
    tree = treeFromString(treeString);
    matcher = pattern.matcher(tree);
    assertTrue(matcher.find());
    assertEquals(treeString, matcher.getMatch().toString());
    Tree node = matcher.getNode("comma");
    assertEquals(",", node.toString());
    assertSame(tree.children()[5], node);
    assertNotSame(tree.children()[3], node);
    assertFalse(matcher.find());
  }

  public void testVariableGroups() {
    String treeString = "(albatross (foo 1) (bar 2))";
    Tree tree = treeFromString(treeString);

    TregexPattern pattern = TregexPattern.compile("/(.*)/#1%name < foo");
    TregexMatcher matcher = pattern.matcher(tree);
    assertTrue(matcher.find());
    assertEquals(treeString, matcher.getMatch().toString());
    assertEquals("albatross", matcher.getVariableString("name"));
    assertFalse(matcher.find());

    pattern = TregexPattern.compile("/(.*)/#1%name < /foo(.*)/#1%blah");
    matcher = pattern.matcher(tree);
    assertTrue(matcher.find());
    assertEquals(treeString, matcher.getMatch().toString());
    assertEquals("albatross", matcher.getVariableString("name"));
    assertEquals("", matcher.getVariableString("blah"));
    assertFalse(matcher.find());

    pattern = TregexPattern.compile("/(.*)/#1%name < (/(.*)/#1%blah < __)");
    matcher = pattern.matcher(tree);
    assertTrue(matcher.find());
    assertEquals(treeString, matcher.getMatch().toString());
    assertEquals("albatross", matcher.getVariableString("name"));
    assertEquals("foo", matcher.getVariableString("blah"));
    assertTrue(matcher.find());
    assertEquals(treeString, matcher.getMatch().toString());
    assertEquals("albatross", matcher.getVariableString("name"));
    assertEquals("bar", matcher.getVariableString("blah"));
    assertFalse(matcher.find());

    treeString = "(albatross (foo foo_albatross) (bar foo_albatross))";
    tree = treeFromString(treeString);
    pattern = TregexPattern.compile("/(.*)/#1%name < (/(.*)/#1%blah < /(.*)_(.*)/#2%name)");
    matcher = pattern.matcher(tree);
    assertTrue(matcher.find());
    assertEquals(treeString, matcher.getMatch().toString());
    assertEquals("albatross", matcher.getVariableString("name"));
    assertEquals("foo", matcher.getVariableString("blah"));
    assertTrue(matcher.find());
    assertEquals(treeString, matcher.getMatch().toString());
    assertEquals("albatross", matcher.getVariableString("name"));
    assertEquals("bar", matcher.getVariableString("blah"));
    assertFalse(matcher.find());

    pattern = TregexPattern.compile("/(.*)/#1%name < (/(.*)/#1%blah < /(.*)_(.*)/#1%blah#2%name)");
    matcher = pattern.matcher(tree);
    assertTrue(matcher.find());
    assertEquals(treeString, matcher.getMatch().toString());
    assertEquals("albatross", matcher.getVariableString("name"));
    assertEquals("foo", matcher.getVariableString("blah"));
    assertFalse(matcher.find());

    // TODO: there is a subtle bug in Tregex hinted at by the
    // construction of this test.  Suppose you have two regex patterns
    // such as /(.*)/#1%name and /(.*)/#1%blah in the pattern.  You
    // should then be able to write another regex
    // /(.*)(.*)/#1%blah#2%name and have it match the concatenation of
    // the two patterns.  However, this doesn't work, as the first two
    // characters of the node get matched regardless of what "blah"
    // and "name" are, resulting in the groups not matching.
  }


  public void testParenthesizedExpressions() {
    String[] treeStrings = { "( (S (S (PP (IN In) (NP (CD 1941) )) (, ,) (NP (NP (NNP Raeder) ) (CC and) (NP (DT the) (JJ German) (NN navy) )) (VP (VBD threatened) (S (VP (TO to) (VP (VB attack) (NP (DT the) (NNP Panama) (NNP Canal) )))))) (, ,) (RB so) (S (NP (PRP we) ) (VP (VBD created) (NP (NP (DT the) (NNP Southern) (NNP Command) ) (PP-LOC (IN in) (NP (NNP Panama) ))))) (. .) ))",
                             "(S (S (NP-SBJ (NNP Japan) ) (VP (MD can) (VP (VP (VB grow) ) (CC and) (VP (RB not) (VB cut) (PRT (RB back) ))))) (, ,) (CC and) (RB so) (S (ADVP (RB too) ) (, ,) (NP (NP (NNP New) (NNP Zealand) )) ))))",
                             "( (S (S (NP-SBJ (PRP You) ) (VP (VBP make) (NP (DT a) (NN forecast) ))) (, ,) (CC and) (RB then) (S (NP-SBJ (PRP you) ) (VP (VBP become) (NP-PRD (PRP$ its) (NN prisoner) ))) (. .)))" };

    Tree[] trees = treesFromString(treeStrings);

    // First pattern: no parenthesized expressions.  All three trees should match once.
    TregexPattern pattern = TregexPattern.compile("/^S/ < (/^S/ $++ (/^[,]|CC|CONJP$/ $+ (RB=adv $+ /^S/)))");
    TregexMatcher matcher = pattern.matcher(trees[0]);
    assertTrue(matcher.find());
    assertFalse(matcher.find());

    matcher = pattern.matcher(trees[1]);
    assertTrue(matcher.find());
    assertFalse(matcher.find());

    matcher = pattern.matcher(trees[2]);
    assertTrue(matcher.find());
    assertFalse(matcher.find());

    // Second pattern: single relation in parentheses.  First tree should not match.
    pattern = TregexPattern.compile("/^S/ < (/^S/ $++ (/^[,]|CC|CONJP$/ (< and) $+ (RB=adv $+ /^S/)))");
    matcher = pattern.matcher(trees[0]);
    assertFalse(matcher.find());

    matcher = pattern.matcher(trees[1]);
    assertTrue(matcher.find());
    assertFalse(matcher.find());

    matcher = pattern.matcher(trees[2]);
    assertTrue(matcher.find());
    assertFalse(matcher.find());

    // Third pattern: single relation in parentheses and negated.  Only first tree should match.
    pattern = TregexPattern.compile("/^S/ < (/^S/ $++ (/^[,]|CC|CONJP$/ !(< and) $+ (RB=adv $+ /^S/)))");
    matcher = pattern.matcher(trees[0]);
    assertTrue(matcher.find());
    assertFalse(matcher.find());

    matcher = pattern.matcher(trees[1]);
    assertFalse(matcher.find());

    matcher = pattern.matcher(trees[2]);
    assertFalse(matcher.find());

    // Fourth pattern: double relation in parentheses, no negation.
    pattern = TregexPattern.compile("/^S/ < (/^S/ $++ (/^[,]|CC|CONJP$/ (< and $+ RB) $+ (RB=adv $+ /^S/)))");
    matcher = pattern.matcher(trees[0]);
    assertFalse(matcher.find());

    matcher = pattern.matcher(trees[1]);
    assertTrue(matcher.find());
    assertFalse(matcher.find());

    matcher = pattern.matcher(trees[2]);
    assertTrue(matcher.find());
    assertFalse(matcher.find());

    // Fifth pattern: double relation in parentheses, negated.
    pattern = TregexPattern.compile("/^S/ < (/^S/ $++ (/^[,]|CC|CONJP$/ !(< and $+ RB) $+ (RB=adv $+ /^S/)))");
    matcher = pattern.matcher(trees[0]);
    assertTrue(matcher.find());
    assertFalse(matcher.find());

    matcher = pattern.matcher(trees[1]);
    assertFalse(matcher.find());

    matcher = pattern.matcher(trees[2]);
    assertFalse(matcher.find());

    // Six pattern: double relation in parentheses, negated.  The only
    // tree with "and then" is the third one, so that is the one tree
    // that should not match.
    pattern = TregexPattern.compile("/^S/ < (/^S/ $++ (/^[,]|CC|CONJP$/ !(< and $+ (RB < then)) $+ (RB=adv $+ /^S/)))");
    matcher = pattern.matcher(trees[0]);
    assertTrue(matcher.find());
    assertFalse(matcher.find());

    matcher = pattern.matcher(trees[1]);
    assertTrue(matcher.find());
    assertFalse(matcher.find());

    matcher = pattern.matcher(trees[2]);
    assertFalse(matcher.find());
  }

  /**
   * The PARENT_EQUALS relation allows for a simplification of what
   * would have been a pair of rules in the dependencies.
   */
  public void testParentEquals() {
    runTest("A <= B", "(A (B 1))", "(A (B 1))");
    // Note that if the child node is the same as the parent node, a
    // double match is expected if there is nothing to eliminate it in
    // the expression
    runTest("A <= A", "(A (A 1) (B 2))", "(A (A 1) (B 2))", "(A (A 1) (B 2))", "(A 1)");
    // This is the kind of expression where this relation can be useful
    runTest("A <= (A < B)", "(A (A (B 1)))", "(A (A (B 1)))", "(A (B 1))");
    runTest("A <= (A < B)", "(A (A (B 1)) (A (C 2)))", "(A (A (B 1)) (A (C 2)))", "(A (B 1))");
    runTest("A <= (A < B)", "(A (A (C 2)))");
  }

  /**
   * Test a few possible ways to make disjunctions at the root level.
   * Note that disjunctions at lower levels can always be created by
   * repeating the relation, but that is not true at the root, since
   * the root "relation" is implicit.
   */
  public void testRootDisjunction() {
    runTest("A | B", "(A (B 1))", "(A (B 1))", "(B 1)");

    runTest("(A) | (B)", "(A (B 1))", "(A (B 1))", "(B 1)");

    runTest("A < B | A < C", "(A (B 1) (C 2))", "(A (B 1) (C 2))", "(A (B 1) (C 2))");

    runTest("A < B | B < C", "(A (B 1) (C 2))", "(A (B 1) (C 2))");
    runTest("A < B | B < C", "(A (B (C 1)) (C 2))", "(A (B (C 1)) (C 2))", "(B (C 1))");

    runTest("A | B | C", "(A (B (C 1)) (C 2))", "(A (B (C 1)) (C 2))", "(B (C 1))", "(C 1)", "(C 2)");

    // The binding of the | should look like this:
    // A ( (< B) | (< C) )
    runTest("A < B | < C", "(A (B 1))", "(A (B 1))");
    runTest("A < B | < C", "(A (B 1) (C 2))", "(A (B 1) (C 2))", "(A (B 1) (C 2))");
    runTest("A < B | < C", "(B (C 1))");
  }


  /**
   * Tests the subtree pattern, <code>&lt;...</code>, which checks for
   * an exact subtree under our current tree
   */
  public void testSubtreePattern() {
    // test the obvious expected matches and several expected match failures
    runTest("A <... { B ; C ; D }", "(A (B 1) (C 2) (D 3))", "(A (B 1) (C 2) (D 3))");
    runTest("A <... { B ; C ; D }", "(Z (A (B 1) (C 2) (D 3)))", "(A (B 1) (C 2) (D 3))");
    runTest("A <... { B ; C ; D }", "(A (B 1) (C 2) (D 3) (E 4))");
    runTest("A <... { B ; C ; D }", "(A (E 4) (B 1) (C 2) (D 3))");
    runTest("A <... { B ; C ; D }", "(A (B 1) (C 2) (E 4) (D 3))");
    runTest("A <... { B ; C ; D }", "(A (B 1) (C 2))");

    // every test above should return the opposite when negated
    runTest("A !<... { B ; C ; D }", "(A (B 1) (C 2) (D 3))");
    runTest("A !<... { B ; C ; D }", "(Z (A (B 1) (C 2) (D 3)))");
    runTest("A !<... { B ; C ; D }", "(A (B 1) (C 2) (D 3) (E 4))", "(A (B 1) (C 2) (D 3) (E 4))");
    runTest("A !<... { B ; C ; D }", "(A (E 4) (B 1) (C 2) (D 3))", "(A (E 4) (B 1) (C 2) (D 3))");
    runTest("A !<... { B ; C ; D }", "(A (B 1) (C 2) (E 4) (D 3))", "(A (B 1) (C 2) (E 4) (D 3))");
    runTest("A !<... { B ; C ; D }", "(A (B 1) (C 2))", "(A (B 1) (C 2))");

    // test a couple various forms of nesting
    runTest("A <... { (B < C) ; D }", "(A (B (C 2)) (D 3))", "(A (B (C 2)) (D 3))");
    runTest("A <... { (B <... { C ; D }) ; E }", "(A (B (C 2) (D 3)) (E 4))", "(A (B (C 2) (D 3)) (E 4))");
    runTest("A <... { (B !< C) ; D }", "(A (B (C 2)) (D 3))");
  }

  public void testDisjunctionVariableAssignments() {
    Tree tree = treeFromString("(NP (UCP (NNP U.S.) (CC and) (ADJP (JJ northern) (JJ European))) (NNS diplomats))");
    TregexPattern pattern = TregexPattern.compile("UCP [ <- (ADJP=adjp < JJR) | <, NNP=np ]");
    TregexMatcher matcher = pattern.matcher(tree);
    assertTrue(matcher.find());
    assertEquals("(NNP U.S.)", matcher.getNode("np").toString());
    assertFalse(matcher.find());
  }

  public void testOptional() {
    Tree tree = treeFromString("(A (B (C 1)) (B 2))");
    TregexPattern pattern = TregexPattern.compile("B ? < C=c");
    TregexMatcher matcher = pattern.matcher(tree);
    assertTrue(matcher.find());
    assertEquals("(C 1)", matcher.getNode("c").toString());
    assertTrue(matcher.find());
    assertEquals(null, matcher.getNode("c"));
    assertFalse(matcher.find());

    tree = treeFromString("(ROOT (INTJ (CC But) (S (NP (DT the) (NNP RTC)) (ADVP (RB also)) (VP (VBZ requires) (`` ``) (S (FRAG (VBG working) ('' '') (NP (NP (NN capital)) (S (VP (TO to) (VP (VB maintain) (SBAR (S (NP (NP (DT the) (JJ bad) (NNS assets)) (PP (IN of) (NP (NP (NNS thrifts)) (SBAR (WHNP (WDT that)) (S (VP (VBP are) (VBN sold) (, ,) (PP (IN until) (NP (DT the) (NNS assets))))))))) (VP (MD can) (VP (VB be) (VP (VBN sold) (ADVP (RB separately))))))))))))))) (S (VP (. .)))))");
    // a pattern used to rearrange punctuation nodes in the srparser
    pattern = TregexPattern.compile("__ !> __ <- (__=top <- (__ <<- (/[.]|PU/=punc < /[.!?。！？]/ ?> (__=single <: =punc))))");
    matcher = pattern.matcher(tree);
    assertTrue(matcher.find());
    assertEquals("(. .)", matcher.getNode("punc").toString());
    assertEquals("(VP (. .))", matcher.getNode("single").toString());
    assertFalse(matcher.find());
  }

  /** The parser should not allow a pattern which is both negated and optional */
  public void testNegatedOptional() {
    TregexPattern pattern;
    // these should be fine
    pattern = TregexPattern.compile("A ?< B");
    pattern = TregexPattern.compile("A !< B");
    // this should break
    try {
      pattern = TregexPattern.compile("A !?< B");
    } catch (TregexParseException e) {
      // yay, passed
    }
    // this should also break
    try {
      pattern = TregexPattern.compile("A ?!< B");
    } catch (TregexParseException e) {
      // yay, passed
    }

    // these should be fine
    pattern = TregexPattern.compile("A ?(< B < C)");
    pattern = TregexPattern.compile("A !(< B < C)");
    // this should break
    try {
      pattern = TregexPattern.compile("A !?(< B < C)");
    } catch (TregexParseException e) {
      // yay, passed
    }
    // this should also break
    try {
      pattern = TregexPattern.compile("A ?!(< B < C)");
    } catch (TregexParseException e) {
      // yay, passed
    }
  }

  public void testOptionalToString() {
    TregexPattern pattern;
    pattern = TregexPattern.compile("A ?(< B < C)");
    assertEquals("Root (A ?(< B < C ))", pattern.toString());

    pattern = TregexPattern.compile("A ?< B");
    assertEquals("Root (A ?< B )", pattern.toString());

    pattern = TregexPattern.compile("A ?[< B | < C]");
    assertEquals("Root (A ?[< B  | < C ])", pattern.toString());
  }

  /**
   * Tests the subtree pattern, <code>&lt;...</code>, which checks for
   * an exact subtree under our current tree, but test it as an optional relation.
   *<br>
   * Checks a bug reported by @tanloong (https://github.com/stanfordnlp/CoreNLP/issues/1375)
   *<br>
   * The optional subtree should only match exactly once,
   * but its buggy form was an infinite loop
   * (see CoordinationPattern for more notes on why)
   */
  public void testOptionalSubtreePattern() {
    runTest("A ?<... { B ; C ; D }", "(A (B 1) (C 2) (D 3))", "(A (B 1) (C 2) (D 3))");
  }

  /**
   * The bug reported by @tanloong (https://github.com/stanfordnlp/CoreNLP/issues/1375)
   * actually applied to <i>all</i> optional coordination patterns
   *<br>
   * Here we check that a simpler optional conjunction also fails
   */
  public void testOptionalChild() {
    runTest("A ?(< B <C)", "(A (B 1) (C 2) (D 3))", "(A (B 1) (C 2) (D 3))");
  }

  /**
   * An optional coordination which doesn't hit should also match exactly once
   */
  public void testOptionalChildMiss() {
    runTest("A ?(< B < E)", "(A (B 1) (C 2) (D 3))", "(A (B 1) (C 2) (D 3))");
  }

  /**
   * An optional disjunction coordination should match at least once,
   * but not match any extra times just because of the optional
   */
  public void testOptionalDisjunction() {
    // this matches once as an optional, even though none of the children match
    runTest("A ?[< E | < F]", "(A (B 1) (C 2) (D 3))", "(A (B 1) (C 2) (D 3))");

    // this matches twice
    runTest("A ?[< B | < C]", "(A (B 1) (C 2))", "(A (B 1) (C 2))", "(A (B 1) (C 2))");
    // this matches once, since the (< E) is useless
    runTest("A ?[< B | < E]", "(A (B 1) (C 2) (D 3))", "(A (B 1) (C 2) (D 3))");
    // now it will match twice, since the B should match twice
    runTest("A ?[< B | < E]", "(A (B 1) (C 2) (B 3))", "(A (B 1) (C 2) (B 3))", "(A (B 1) (C 2) (B 3))");

    // check by hand that foo & bar are set as expected for the disjunction matches
    // note that the order will be the order of the disjunction then subtrees,
    // not sorted by the order of the subtrees
    TregexPattern pattern = TregexPattern.compile("A ?[< B=foo | < C=bar]");
    Tree tree = treeFromString("(A (B 1) (C 2) (B 3))");
    TregexMatcher matcher = pattern.matcher(tree);
    assertTrue(matcher.find());
    assertEquals("(B 1)", matcher.getNode("foo").toString());
    assertNull(matcher.getNode("bar"));

    assertTrue(matcher.find());
    assertEquals("(B 3)", matcher.getNode("foo").toString());
    assertNull(matcher.getNode("bar"));

    assertTrue(matcher.find());
    assertNull(matcher.getNode("foo"));
    assertEquals("(C 2)", matcher.getNode("bar").toString());

    assertFalse(matcher.find());

    // this example should also work if the same name is used
    // for both of the children!
    pattern = TregexPattern.compile("A ?[< B=foo | < C=foo]");
    matcher = pattern.matcher(tree);
    assertTrue(matcher.find());
    assertEquals("(B 1)", matcher.getNode("foo").toString());

    assertTrue(matcher.find());
    assertEquals("(B 3)", matcher.getNode("foo").toString());

    assertTrue(matcher.find());
    assertEquals("(C 2)", matcher.getNode("foo").toString());

    assertFalse(matcher.find());

  }

  /**
   * A user supplied an example of a negated disjunction which went into an infinite loop.
   * Apparently no one had ever used a negated disjunction of tree structures before!
   * <br>
   * The problem was that the logic at the time tried to backtrack in
   * the disjunction to find a better match, but that resulted in it
   * going back and forth between the failed clause which was accepted
   * and the successful clause which was rejected.  The problem being
   * that the first half of the disjunction doesn't match, so the
   * pattern is successful up to that point, but the second half does
   * match, causing the pattern to be rejected and restarted.
   */
  public void testNegatedDisjunction() {
    runTest("NP![</,/|.(JJ<else)]", "( (NP (NP (NN anyone)) (ADJP (JJ else))))", "(NP (NP (NN anyone)) (ADJP (JJ else)))");
  }

  /**
   * Stores an input and the expected output.  Obviously this is only
   * expected to work with a given pattern, but this is a bit more
   * convenient than calling the same pattern by hand over and over
   */
  public static class TreeTestExample {
    Tree input;
    Tree[] expectedOutput;

    public TreeTestExample(String input, String ... expectedOutput) {
      this.input = treeFromString(input);
      this.expectedOutput = new Tree[expectedOutput.length];
      for (int i = 0; i < expectedOutput.length; ++i) {
        this.expectedOutput[i] = treeFromString(expectedOutput[i]);
      }
    }

    public void outputResults(TregexPattern pattern) {
      System.out.println(pattern + " found the following matches on input " +
                         input);
      TregexMatcher matcher = pattern.matcher(input);
      boolean output = false;
      while (matcher.find()) {
        output = true;
        System.out.println("  " + matcher.getMatch());
        Set<String> namesToNodes = matcher.getNodeNames();
        for (String name : namesToNodes) {
          System.out.println("    " + name + ": " + matcher.getNode(name));
        }
      }
      if (!output) {
        System.out.println("  Nothing!  Absolutely nothing!");
      }
    }

    public void runTest(TregexPattern pattern) {
      IdentityHashMap<Tree, Object> matchedTrees =
        new IdentityHashMap<Tree, Object>();

      TregexMatcher matcher = pattern.matcher(input);
      for (int i = 0; i < expectedOutput.length; ++i) {
        try {
          assertTrue(matcher.find());
        } catch (junit.framework.AssertionFailedError e) {
          throw new RuntimeException("Pattern " + pattern +
                                     " failed on input " + input.toString() +
                                     " [expected " + expectedOutput.length +
                                     " results, got " + i + "]",
                                     e);
        }
        Tree match = matcher.getMatch();
        String result = match.toString();
        String expectedResult = expectedOutput[i].toString();
        try {
          assertEquals(expectedResult, result);
        } catch (junit.framework.AssertionFailedError e) {
          throw new RuntimeException("Pattern " + pattern +
                                     " matched the wrong tree on input " +
                                     input.toString() +
                                     " [expected " + expectedOutput[i] +
                                     " got " + matcher.getMatch() + "]",
                                     e);
        }
        matchedTrees.put(match, null);
      }
      try {
        assertFalse(matcher.find());
      } catch (junit.framework.AssertionFailedError e) {
        throw new RuntimeException("Pattern " + pattern +
                                   " failed on input " + input.toString() +
                                   " [expected " + expectedOutput.length +
                                   " results, got more than that]",
                                   e);
      }

      for (Tree subtree : input) {
        if (matchedTrees.containsKey(subtree)) {
          assertTrue(matcher.matchesAt(subtree));
        } else {
          assertFalse(matcher.matchesAt(subtree));
        }
      }
    }
  }


  /** Check that running the Tregex pattern on the tree gives the
   *  results shown in results.
   */
  public static void runTest(String pattern, String tree,
                             String ... expectedResults) {
    runTest(TregexPattern.compile(pattern), tree, expectedResults);
  }

  public static void runTest(TregexPattern pattern, String tree,
                             String ... expectedResults) {
    TreeTestExample test = new TreeTestExample(tree, expectedResults);
    test.runTest(pattern);
  }

  /**
   * runs a given pattern on many of the above test objects,
   * outputting the results matched for test test case
   */
  public static void outputResults(TregexPattern pattern,
                            TreeTestExample ... tests) {
    for (TreeTestExample test : tests) {
      test.outputResults(pattern);
    }
  }

  public static void outputResults(String pattern, String ... trees) {
    for (String tree : trees) {
      TreeTestExample test = new TreeTestExample(tree);
      test.outputResults(TregexPattern.compile(pattern));
    }
  }
}
