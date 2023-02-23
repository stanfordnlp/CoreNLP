package edu.stanford.nlp.parser.lexparser;

import org.junit.Assert;
import org.junit.Test;

import edu.stanford.nlp.trees.Tree;

public class NegraPennCollinizerTest {
  @Test
  public void testRemovePunct() {
    NegraPennTreebankParserParams tlpp = new NegraPennTreebankParserParams();
    NegraPennCollinizer collinizer = new NegraPennCollinizer(tlpp);

    // Test that the collinizer removes a comma
    // Lazy test writing: just use the English version, updated to work with the German tags
    Tree gold = Tree.valueOf("(ROOT (S (S (NP (PRP I)) (VP (VBP like) (NP (JJ blue) (NN skin)))) ($, ,) (CC and) (S (NP (PRP I)) (VP (MD cannot) (VP (VB lie))))))");
    Tree goldT = collinizer.transformTree(gold, gold);
    Tree goldExpected = Tree.valueOf("(S (S (NP (PRP I)) (VP (VBP like) (NP (JJ blue) (NN skin)))) (CC and) (S (NP (PRP I)) (VP (MD cannot) (VP (VB lie)))))");
    Assert.assertEquals(goldExpected, goldT);

    // Same test, but it should pick up the comma just based on the tag
    gold = Tree.valueOf("(ROOT (S (S (NP (PRP I)) (VP (VBP like) (NP (JJ blue) (NN skin)))) ($, zzzzz) (CC and) (S (NP (PRP I)) (VP (MD cannot) (VP (VB lie))))))");
    goldT = collinizer.transformTree(gold, gold);
    Assert.assertEquals(goldExpected, goldT);

    // Difference with the English: the Negra collinizer does not look at punct words
    // Perhaps that was a mistake?
    gold = Tree.valueOf("(S (S (NP (PRP I)) (VP (VBP like) (NP (JJ blue) (NN skin)))) (CC ,) (CC and) (S (NP (PRP I)) (VP (MD cannot) (VP (VB lie)))))");
    goldT = collinizer.transformTree(gold, gold);
    Assert.assertEquals(gold, goldT);

    // Double check that (CC zzzzz) is not deleted by default
    Tree guess = Tree.valueOf("(S (S (NP (PRP I)) (VP (VBP like) (NP (JJ blue) (NN skin)))) (CC zzzzz) (CC and) (S (NP (PRP I)) (VP (MD cannot) (VP (VB lie)))))");
    Tree guessT = collinizer.transformTree(guess, guess);
    Assert.assertEquals(guess, guessT);

    // Check that the guess tree has the non-punct word removed if it is a punct in the gold tree
    gold = Tree.valueOf("(ROOT (S (S (NP (PRP I)) (VP (VBP like) (NP (JJ blue) (NN skin)))) ($, zzzzz) (CC and) (S (NP (PRP I)) (VP (MD cannot) (VP (VB lie))))))");
    guessT = collinizer.transformTree(guess, gold);
    Assert.assertEquals(goldExpected, guessT);
  }
}
