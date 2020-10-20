package edu.stanford.nlp.trees.international.negra;

import junit.framework.TestCase;

import edu.stanford.nlp.trees.Tree;

/** @author Christopher Manning */
public class NegraPennLanguagePackTest extends TestCase {

  public void testBasicCategory() {
    NegraPennLanguagePack lp1 = new NegraPennLanguagePack(false); // leave(some)GF=false
    NegraPennLanguagePack lp2 = new NegraPennLanguagePack(true); // leave(some)GF = true
    NegraPennTreeReaderFactory trf01 = new NegraPennTreeReaderFactory(0, false, false, lp1); // do nothing
    NegraPennTreeReaderFactory trf02 = new NegraPennTreeReaderFactory(0, false, false, lp2);
    NegraPennTreeReaderFactory trf11 = new NegraPennTreeReaderFactory(1, false, false, lp1); // category and function
    NegraPennTreeReaderFactory trf12 = new NegraPennTreeReaderFactory(1, false, false, lp2);
    NegraPennTreeReaderFactory trf21 = new NegraPennTreeReaderFactory(2, false, false, lp1); // just category
    NegraPennTreeReaderFactory trf22 = new NegraPennTreeReaderFactory(2, false, false, lp2);

    String tree = "( (S (NE-SB Kronos) (VAFIN-HD haben) (VP-OC (PP-MO (APPR-AC mit) (PPOSAT-NK ihrer) (NN-NK Musik)) (NN-OA Brücken) (VVPP-HD geschlagen))) ($. .))";
    String ans1 = "(ROOT (S (NE-SB Kronos) (VAFIN-HD haben) (VP-OC (PP-MO (APPR-AC mit) (PPOSAT-NK ihrer) (NN-NK Musik)) (NN-OA Brücken) (VVPP-HD geschlagen)) ($. .)))";
    String ans21 = "(ROOT (S (NE Kronos) (VAFIN haben) (VP (PP (APPR mit) (PPOSAT ihrer) (NN Musik)) (NN Brücken) (VVPP geschlagen)) ($. .)))";
    String ans22 = "(ROOT (S (NE-SB Kronos) (VAFIN haben) (VP (PP (APPR mit) (PPOSAT ihrer) (NN Musik)) (NN-OA Brücken) (VVPP geschlagen)) ($. .)))";

    Tree t01 = Tree.valueOf(tree, trf01);
    Tree t02 = Tree.valueOf(tree, trf02);
    Tree t11 = Tree.valueOf(tree, trf11);
    Tree t12 = Tree.valueOf(tree, trf12);
    Tree t21 = Tree.valueOf(tree, trf21);
    Tree t22 = Tree.valueOf(tree, trf22);
    assertEquals("T01", ans1, t01.toString());
    assertEquals("T02", ans1, t02.toString());
    assertEquals("T11", ans1, t11.toString());
    assertEquals("T12", ans1, t12.toString());
    assertEquals("T21", ans21, t21.toString());
    assertEquals("T22", ans22, t22.toString());

    String ans = lp1.basicCategory("---CJ");
    assertEquals("BC1", "-", ans);
  }

}
