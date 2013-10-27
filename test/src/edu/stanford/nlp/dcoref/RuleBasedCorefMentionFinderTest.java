package edu.stanford.nlp.dcoref;

import junit.framework.TestCase;

import java.util.List;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.trees.Tree;

/**
 * Test some of the routines used in the coref system
 *
 * @author John Bauer
 */
public class RuleBasedCorefMentionFinderTest extends TestCase {
  public void testFindTreeWithWords() {
    Tree tree = Tree.valueOf("(ROOT (S (NP (PRP$ My) (NN dog)) (ADVP (RB also)) (VP (VBZ likes) (S (VP (VBG eating) (NP (NN sausage))))) (. .)))");

    List<CoreLabel> words = Sentence.toCoreLabelList("eating", "sausage");
    Tree subtree = RuleBasedCorefMentionFinder.findTreeWithWords(tree, words);    
    assertEquals("(VP (VBG eating) (NP (NN sausage)))", subtree.toString());

    // test that not finding the phrase doesn't cause us to blow up
    words = Sentence.toCoreLabelList("Restoration", "Angel");
    subtree = RuleBasedCorefMentionFinder.findTreeWithWords(tree, words);
    assertEquals(null, subtree);
  }
}
