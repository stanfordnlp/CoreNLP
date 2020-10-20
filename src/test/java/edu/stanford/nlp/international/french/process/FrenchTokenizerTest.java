package edu.stanford.nlp.international.french.process;

import org.junit.Test;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.PTBTokenizerTest;
import edu.stanford.nlp.process.TokenizerFactory;


/** @author John Bauer
 */
public class FrenchTokenizerTest {

  private final String[] ptbInputs = {
    "Cette fusion est une conséquence directe de la fin de la guerre froide.",
    "Cette fusion est une conséquence directe (de la fin de la guerre froide).",
  };

  private final String[][] ptbGold = {
    { "Cette", "fusion", "est", "une", "conséquence", "directe", "de", "la", "fin", "de", "la", "guerre", "froide", "." },
    { "Cette", "fusion", "est", "une", "conséquence", "directe", "(", "de", "la", "fin", "de", "la", "guerre", "froide", ")", "." }
  };

  private final String[][] ptbGoldParens = {
    { "Cette", "fusion", "est", "une", "conséquence", "directe", "de", "la", "fin", "de", "la", "guerre", "froide", "." },
    { "Cette", "fusion", "est", "une", "conséquence", "directe", "-LRB-", "de", "la", "fin", "de", "la", "guerre", "froide", "-RRB-", "." }
  };

  @Test
  public void testFrenchTokenizer() {
    TokenizerFactory<CoreLabel> tokFactory = FrenchTokenizer.factory();
    PTBTokenizerTest.runOnTwoArrays(tokFactory, ptbInputs, ptbGold);

    tokFactory.setOptions("normalizeParentheses=true");
    PTBTokenizerTest.runOnTwoArrays(tokFactory, ptbInputs, ptbGoldParens);
  }
}
