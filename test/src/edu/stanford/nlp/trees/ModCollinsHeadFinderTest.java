package edu.stanford.nlp.trees;

import junit.framework.TestCase;

/** @author Christopher Manning */
public class ModCollinsHeadFinderTest extends TestCase {

  private HeadFinder hf = new ModCollinsHeadFinder();

  private Tree[] testTrees = {
    Tree.valueOf("(PRN (: --) (S-ADV (NP-SBJ (DT that)) (VP (VBZ is))) (, ,) (SQ (MD can) (NP-SBJ (NP (DT the) (NN network)) (ADVP (RB alone))) (VP (VB make) (NP (DT a) (NN profit)) (PP-CLR (IN on) (NP (PRP it))))))))))))"),
    Tree.valueOf("(NP (NP (NML (DT the) (NNP Secretary)) (POS 's)) (NML (`` ``) (JJ discretionary) (NN fund) (, ,) ('' '')))"),
    Tree.valueOf("(S (NP (NP (NNP Sam)) (, ,) (NP (PRP$ my) (NN brother)) (, ,)) (VP (VBZ eats) (NP (JJ red) (NN meat))) (. .))"),
    Tree.valueOf("(NP (NP (DT The) (JJ Australian) (NNP Broadcasting) (NNP Corporation)) (PRN (-LRB- -LRB-) (NP (NNP ABC)) (-RRB- -RRB-)) (. .))"),
    Tree.valueOf("(PRN (-LRB- -LRB-) (NP (NNP ABC)) (-RRB- -RRB-))"),
    // junk tree just for testing setCategoriesToAvoid (NP never matches VBZ but shouldn't pick the punctuation marks)
    Tree.valueOf("(NP (. .) (. .) (VBZ eats) (. .) (. .))"),
    Tree.valueOf("(PP (SYM -) (NP (CD 3))))"),
    // Tree.valueOf("(FOO (BAR a) (BAZ b))")  // todo: If change to always do something rather than Exception (and edit hfFeads array)
  };

  private String[] hfHeads = { "SQ", "NML", "VP", "NP", "NP", "VBZ", "SYM" // , "BAR"
  };

  private void runTesting(HeadFinder hf, String[] heads) {
    assertEquals("Test arrays out of balance", testTrees.length, heads.length);
    for (int i = 0; i < testTrees.length; i++) {
      Tree h = hf.determineHead(testTrees[i]);
      String headCat = h.value();
      assertEquals("Wrong head found", heads[i], headCat);
    }
  }

  public void testModCollinsHeadFinder() {
    runTesting(hf, hfHeads);
  }

}
