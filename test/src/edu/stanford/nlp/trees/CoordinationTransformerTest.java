package edu.stanford.nlp.trees;

import junit.framework.TestCase;

/**
 * Test some various error and success cases for the CoordinationTransformer
 *
 * @author John Bauer
 */
public class CoordinationTransformerTest extends TestCase {

  static final String SYM_DONT_MOVE_RB = "(ROOT (S (NP (NP (NN fire) (NN gear)) (, ,) (ADVP (RB annually)) (SYM fy) (: -)) (VP (NN fy) (: :))))";

  public void testMoveRB() {
    Tree test = Tree.valueOf(SYM_DONT_MOVE_RB);
    Tree result = CoordinationTransformer.moveRB(test);
    assertEquals(test.toString(), result.toString());
  }
}
