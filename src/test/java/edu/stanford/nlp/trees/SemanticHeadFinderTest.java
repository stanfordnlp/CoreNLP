package edu.stanford.nlp.trees;

import junit.framework.TestCase;

/** @author Christopher Manning */
public class SemanticHeadFinderTest extends TestCase {

  private final HeadFinder shf = new SemanticHeadFinder();
  private final HeadFinder shfc = new SemanticHeadFinder(false);

  private Tree[] testTrees = {
          Tree.valueOf("(WHNP (WHADJP (WRB How) (JJ many)) (NNS cars))"),
          Tree.valueOf("(VP (VBZ is) (NP-PRD (DT a) (NN champion)))"),
          Tree.valueOf("(VP (VBZ has) (VP (VBN been) (VP (VBG feeling) (ADJP (JJ unwell)))))"),
          Tree.valueOf("(VP (VBG being) (NP (DT an) (NN idiot)))"),
          Tree.valueOf("(SBAR (WHNP (WDT that)) (S (NP (PRP you)) (VP (VB understand) (NP (PRP me)))))"),
          Tree.valueOf("(VP (VBD was) (VP (VBN defeated) (PP (IN by) (NP (NNP Clinton)))))"),
          Tree.valueOf("(VP (VBD was) (VP (VBG eating) (NP (NN pizza))))"),
          Tree.valueOf("(VP (VBN been) (VP (VBN overtaken)))"),
          Tree.valueOf("(VP (VBN been) (NP (DT a) (NN liar)))"),
          Tree.valueOf("(VP (VBZ is) (VP (VP (VBN purged) (PP (IN of) (NP (JJ threatening) (NNS elements)))) (, ,) (VP (VBN served) (PRT (RP up)) (PP (IN in) (NP (JJ bite-sized) (NNS morsels)))) (CC and) (VP (VBN accompanied) (PP (IN by) (NP-LGS (NNS visuals))))))"),
          Tree.valueOf("(VP (TO na) (VP (VB say) (NP (WP who)))))"), // complement in "I 'm not gon na say who"
          Tree.valueOf("(VP (VBZ s) (RB not) (NP-PRD (NP (DT any)) (PP (IN of) (NP (PRP you)))))"), // complement of "Its not any of you
          Tree.valueOf("(VP (VBZ ve) (VP (VBN been) (VP (VBG feeling) (ADJP (JJ unwell)))))"), // complement of "Ive been feeling unwell
          Tree.valueOf("(PP (SYM -) (NP (CD 3))))"),
          Tree.valueOf("(VP (`` \") (VBN forced) ('' \") (PP-CLR (IN into) (S-NOM (VP (VBG taking) (NP (DT a) (JJ hawkish) (NN line))))))"),

  };

  private String[] shfHeads = { "NNS", "NP", "VP", "NP", "S", "VP", "VP", "VP", "NP", "VP", "VP", "NP", "VP", "SYM", "VBN" };

  private String[] shfcHeads = { "NNS", "VBZ", "VP", "VBG", "S", "VP", "VP", "VP", "VBN", "VP", "VP", "VBZ", "VP", "SYM", "VBN" };

  private void runTesting(HeadFinder hf, String[] heads) {
    assertEquals("Test arrays out of balance", testTrees.length, heads.length);
    for (int i = 0; i < testTrees.length; i++) {
      Tree h = hf.determineHead(testTrees[i]);
      String headCat = h.value();
      assertEquals("Wrong head found", heads[i], headCat);
    }
  }

  public void testRegularSemanticHeadFinder() {
    runTesting(shf, shfHeads);
  }

  public void testCopulaHeadSemanticHeadFinder() {
    runTesting(shfc, shfcHeads);
  }

}
