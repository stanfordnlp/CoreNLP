package edu.stanford.nlp.dcoref;

import junit.framework.TestCase;

import edu.stanford.nlp.trees.Tree;

/**
 * Test some of the routines used in the coref system
 *
 * @author John Bauer
 */
public class RuleBasedCorefMentionFinderTest extends TestCase {
  public void testFindTreeWithSmallestSpan() {
    Tree tree = Tree.valueOf("(ROOT (S (NP (PRP$ My) (NN dog)) (ADVP (RB also)) (VP (VBZ likes) (S (VP (VBG eating) (NP (NN sausage))))) (. .)))");
    tree.indexSpans();
    Tree subtree = RuleBasedCorefMentionFinder.findTreeWithSmallestSpan(tree, 0, 2);
    assertEquals("(NP (PRP$ My) (NN dog))", subtree.toString());

    subtree = RuleBasedCorefMentionFinder.findTreeWithSmallestSpan(tree, 0, 1);
    assertEquals("My", subtree.toString());
  }
}
