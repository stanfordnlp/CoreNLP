package edu.stanford.nlp.trees;

import junit.framework.TestCase;

/**
 * Test Trees.java
 *
 * @author John Bauer
 */
public class TreesTest extends TestCase {
  public void testHeight() {
    assertEquals(1, Trees.height(Tree.valueOf("(2)")));
    assertEquals(2, Trees.height(Tree.valueOf("(2 3)")));
    assertEquals(2, Trees.height(Tree.valueOf("(2 3 4 5 6)")));
    assertEquals(3, Trees.height(Tree.valueOf("(2 (3 4))")));
    assertEquals(10, Trees.height(Tree.valueOf("(1 (2 (3 (4 (5 (6 (7 (8 (9 10)))))))))")));
  }
}

