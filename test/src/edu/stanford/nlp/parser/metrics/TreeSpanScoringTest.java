package edu.stanford.nlp.parser.metrics;

import junit.framework.TestCase;

import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;

public class TreeSpanScoringTest extends TestCase {
  TreebankLanguagePack tlp = new PennTreebankLanguagePack();

  public void testNoErrors() {
    Tree t1 = Tree.valueOf("(ROOT (S (NP (PRP$ My) (NN dog)) (ADVP (RB also)) (VP (VBZ likes) (S (VP (VBG eating) (NP (NN sausage))))) (. .)))");

    assertEquals(0, TreeSpanScoring.countSpanErrors(tlp, t1, t1));
  }


  public void testTagErrors() {
    Tree t1 = Tree.valueOf("(ROOT (S (NP (PRP$ My) (NN dog)) (ADVP (RB also)) (VP (VBZ likes) (S (VP (VBG eating) (NP (NN sausage))))) (. .)))");
    Tree t2 = Tree.valueOf("(ROOT (S (NP (PRP$ My) (NN dog)) (ADVP (RB also)) (VP (VBZ likes) (S (VP (VBG eating) (NP (VBG sausage))))) (. .)))");

    assertEquals(2, TreeSpanScoring.countSpanErrors(tlp, t1, t2));
    assertEquals(2, TreeSpanScoring.countSpanErrors(tlp, t2, t1));
  }

  public void testMislabeledSpans() {
    Tree t1 = Tree.valueOf("(ROOT (S (NP (PRP$ My) (NN dog)) (ADVP (RB also)) (VP (VBZ likes) (S (VP (VBG eating) (NP (NN sausage))))) (. .)))");
    Tree t2 = Tree.valueOf("(ROOT (S (NP (PRP$ My) (NN dog)) (ADVP (RB also)) (VP (VBZ likes) (ADVP (VP (VBG eating) (NP (NN sausage))))) (. .)))");

    assertEquals(2, TreeSpanScoring.countSpanErrors(tlp, t1, t2));
    assertEquals(2, TreeSpanScoring.countSpanErrors(tlp, t2, t1));
  }

  public void testExtraSpan() {
    Tree t1 = Tree.valueOf("(ROOT (S (NP (PRP$ My) (NN dog)) (ADVP (RB also)) (VP (VBZ likes) (S (VP (VBG eating) (NP (NN sausage))))) (. .)))");
    Tree t2 = Tree.valueOf("(ROOT (S (NP (PRP$ My) (NN dog)) (ADVP (RB also)) (ADVP (VP (VBZ likes) (S (VP (VBG eating) (NP (NN sausage)))))) (. .)))");

    assertEquals(1, TreeSpanScoring.countSpanErrors(tlp, t1, t2));
  }

  public void testMissingSpan() {
    Tree t1 = Tree.valueOf("(ROOT (S (NP (PRP$ My) (NN dog)) (ADVP (RB also)) (VP (VBZ likes) (S (VP (VBG eating) (NP (NN sausage))))) (. .)))");
    Tree t2 = Tree.valueOf("(ROOT (S (NP (PRP$ My) (NN dog)) (ADVP (RB also)) (VP (VBZ likes) (VP (VBG eating) (NP (NN sausage)))) (. .)))");

    assertEquals(1, TreeSpanScoring.countSpanErrors(tlp, t1, t2));
  }
}
