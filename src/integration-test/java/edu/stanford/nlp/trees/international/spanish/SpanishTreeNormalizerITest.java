package edu.stanford.nlp.trees.international.spanish;

import java.io.IOException;
import java.io.StringReader;

import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.PennTreeReader;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.util.Pair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Jon Gauthier
 */
public class SpanishTreeNormalizerITest {

  private TreeFactory tf;
  private SpanishTreeNormalizer tn;

  @Before
  public void setUp() {
    tf = new LabeledScoredTreeFactory();
    tn = new SpanishTreeNormalizer(true, true, true);
  }

  @SuppressWarnings("unchecked")
  private Pair<String, String>[] multiWordTestCases = new Pair[] {
    // Simplest case
    new Pair("(a (b c_d))",
             "(a (MW_PHRASE?_b (MW? c) (MW? d)))"),

    // New MW phrase should merge with grup.nom head
    new Pair("(grup.nom (np00000 Josep_Maria_Ollé))",
             "(MW_PHRASE?_np00000 (MW? Josep) (MW? Maria) (MW? Ollé))"),

    // Likewise here: new MW phrase should merge with grup.nom head
    new Pair("(grup.nom (grup.nom (nc0p000 productos)) (sp (prep (sp000 de)) (sn (grup.nom (np00000 American_Online)))))",
             "(grup.nom (grup.nom (nc0p000 productos)) (sp (prep (sp000 de)) (sn (MW_PHRASE?_np00000 (MW? American) (MW? Online)))))"),

    // Two multi-word tokens as siblings
    new Pair("(a (b c_d) (b e_f))",
             "(a (MW_PHRASE?_b (MW? c) (MW? d)) (MW_PHRASE?_b (MW? e) (MW? f)))"),

    // Quotation mark "words" should be separated
    new Pair("(a (b \"cde\"))",
             "(a (MW_PHRASE?_b (MW? \") (MW? cde) (MW? \")))"),

    // Hyphenated expression should be separated, with hyphen retained
    new Pair("(a (b tecno-pop))",
             "(a (MW_PHRASE?_b (MW? tecno) (MW? -) (MW? pop)))"),

    // Hyphenated expression with bound morpheme should not be separated
    new Pair("(a (b co-promotora))",
             "(a (b co-promotora))"),

    // Don't bork when we see a bound morpheme without following hyphen
    new Pair("(a (b co) (b promotora))",
             "(a (b co) (b promotora))"),

    // Don't treat commas as multiword separators if they are part of a
    // decimal number expression
    new Pair("(a (b 8,39))", "(a (b 8,39))"),
    new Pair("(a (b 28,91%))", "(a (MW_PHRASE?_b (MW? 28,91) (MW? %)))"),

    // But do treat commas as multiword separators otherwise
    new Pair("(a (b entonces,_yo))", "(a (MW_PHRASE?_b (MW? entonces) (MW? ,) (MW? yo)))"),
  };

  @Test
  public void testMultiWordNormalization() {
    for (Pair<String, String> testCase : multiWordTestCases) {
      Tree head = readTree(testCase.first());
      for (Tree t : head) {
        if (t.isPrePreTerminal())
          tn.normalizeForMultiWord(t, tf);
      }

      Assert.assertEquals(testCase.second(), head.toString());
    }
  }

  /**
   * Read a tree from a PTB-style serialized form in the given string.
   */
  private Tree readTree(String treeRep) {
    try {
      return new PennTreeReader(new StringReader(treeRep), tf).readTree();
    } catch (IOException e) { return null; }
  }

}
