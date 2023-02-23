package edu.stanford.nlp.trees.international.pennchinese;

import org.junit.Assert;
import org.junit.Test;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.international.pennchinese.ChineseTreebankLanguagePack;

public class ChineseCollinizerTest {
  @Test
  public void testRemovePunct() {
    ChineseTreebankLanguagePack tlp = new ChineseTreebankLanguagePack();
    ChineseCollinizer collinizer = new ChineseCollinizer(tlp);

    // Test that the collinizer removes a comma
    // Lazy test writing: just use the English version, updated to work with the Chinese tags
    Tree gold = Tree.valueOf("(ROOT (S (S (NP (PRP I)) (VP (VBP like) (NP (JJ blue) (NN skin)))) (PU ,) (CC and) (S (NP (PRP I)) (VP (MD cannot) (VP (VB lie))))))");
    Tree goldT = collinizer.transformTree(gold, gold);
    Tree goldExpected = Tree.valueOf("(S (S (NP (PRP I)) (VP (VBP like) (NP (JJ blue) (NN skin)))) (CC and) (S (NP (PRP I)) (VP (MD cannot) (VP (VB lie)))))");
    Assert.assertEquals(goldExpected, goldT);

    // Same test, but it should pick up the comma just based on the tag
    gold = Tree.valueOf("(ROOT (S (S (NP (PRP I)) (VP (VBP like) (NP (JJ blue) (NN skin)))) (PU zzzzz) (CC and) (S (NP (PRP I)) (VP (MD cannot) (VP (VB lie))))))");
    goldT = collinizer.transformTree(gold, gold);
    Assert.assertEquals(goldExpected, goldT);

    // It should also pick up the comma based on the word
    gold = Tree.valueOf("(S (S (NP (PRP I)) (VP (VBP like) (NP (JJ blue) (NN skin)))) (CC ,) (CC and) (S (NP (PRP I)) (VP (MD cannot) (VP (VB lie)))))");
    goldT = collinizer.transformTree(gold, gold);
    Assert.assertEquals(goldExpected, goldT);

    // Double check that (CC zzzzz) is not deleted by default
    Tree guess = Tree.valueOf("(S (S (NP (PRP I)) (VP (VBP like) (NP (JJ blue) (NN skin)))) (CC zzzzz) (CC and) (S (NP (PRP I)) (VP (MD cannot) (VP (VB lie)))))");
    Tree guessT = collinizer.transformTree(guess, guess);
    Assert.assertEquals(guess, guessT);

    // Check that the guess tree has the non-punct word removed if it is a punct in the gold tree
    guessT = collinizer.transformTree(guess, gold);
    Assert.assertEquals(goldExpected, guessT);
  }
}
