package edu.stanford.nlp.trees.international.spanish;

import java.io.IOException;
import java.io.StringReader;

import junit.framework.TestCase;

import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.PennTreeReader;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.util.Pair;

/**
 * @author Jon Gauthier
 */
public class SpanishTreeNormalizerTest extends TestCase {

  private TreeFactory tf;
  private SpanishTreeNormalizer tn;

  public void setUp() {
    tf = new LabeledScoredTreeFactory();
    tn = new SpanishTreeNormalizer(true, true);
  }

  @SuppressWarnings("unchecked")
  Pair<String, String>[] multiWordTestCases = new Pair[] {
    new Pair("(grup.nom (np00000 Josep_Maria_Ollé))",
             "(grup.nom (MW_PHRASE?_np00000 (MW? Josep) (MW? Maria) (MW? Ollé)))"),

    new Pair("(grup.nom (grup.nom (nc0p000 productos)) (sp (prep (sp000 de)) (sn (grup.nom (np00000 American_Online)))))",
             "(grup.nom (grup.nom (nc0p000 productos)) (sp (prep (sp000 de)) (sn (grup.nom (MW_PHRASE?_np00000 (MW? American) (MW? Online))))))"),

    // Two multi-word tokens as siblings
    new Pair("(a (b c_d) (b e_f))",
             "(a (MW_PHRASE?_b (MW? c) (MW? d)) (MW_PHRASE?_b (MW? e) (MW? f)))"),

    // Quotation mark "words" should be separated
    new Pair("(a (b \"cde\"))",
             "(a (MW_PHRASE?_b (MW? \") (MW? cde) (MW? \")))"),
  };

  public void testMultiWordNormalization() {
    for (Pair<String, String> testCase : multiWordTestCases) {
      Tree head = readTree(testCase.first());
      for (Tree t : head) {
        if (t.isPrePreTerminal())
          tn.normalizeForMultiWord(t, tf);
      }

      assertEquals(testCase.second(), head.toString());
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
