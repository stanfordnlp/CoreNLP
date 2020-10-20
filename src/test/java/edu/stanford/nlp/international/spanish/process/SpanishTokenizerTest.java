package edu.stanford.nlp.international.spanish.process;

import org.junit.Test;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.PTBTokenizerTest;
import edu.stanford.nlp.process.TokenizerFactory;


/** @author John Bauer
 */
public class SpanishTokenizerTest {

  private final String[] ptbInputs = {
    "Tanto fue así que el club coruñés contempló la posibilidad de suspender el partido.",
    "Tanto fue así que el club coruñés contempló la posibilidad de suspender (el partido).",
  };

  private final String[][] ptbGold = {
    {"Tanto", "fue", "así", "que", "el", "club", "coruñés", "contempló", "la", "posibilidad", "de", "suspender", "el", "partido", "."},
    {"Tanto", "fue", "así", "que", "el", "club", "coruñés", "contempló", "la", "posibilidad", "de", "suspender", "(", "el", "partido", ")", "."},
  };

  private final String[][] ptbGoldParens = {
    {"Tanto", "fue", "así", "que", "el", "club", "coruñés", "contempló", "la", "posibilidad", "de", "suspender", "el", "partido", "."},
    {"Tanto", "fue", "así", "que", "el", "club", "coruñés", "contempló", "la", "posibilidad", "de", "suspender", "-LRB-", "el", "partido", "-RRB-", "."},
  };

  @Test
  public void testSpanishTokenizer() {
    TokenizerFactory<CoreLabel> tokFactory = SpanishTokenizer.factory();
    PTBTokenizerTest.runOnTwoArrays(tokFactory, ptbInputs, ptbGold);

    tokFactory.setOptions("normalizeParentheses=true");
    PTBTokenizerTest.runOnTwoArrays(tokFactory, ptbInputs, ptbGoldParens);
  }
}
