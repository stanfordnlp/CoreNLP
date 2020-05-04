package edu.stanford.nlp.process;

import junit.framework.TestCase;

import java.io.StringReader;
import java.util.List;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;

public class WhitespaceTokenizerTest extends TestCase {

  public static final String[] TEST = {
    "This is a test . \n This is a second line .",
    "A \n B \n \n C",
    "A. B",
    "皇后\u3000\u3000後世 and (800)\u00A0326-1456",
  };

  public static final String[][] RESULTS_NO_EOL = {
    {"This", "is", "a", "test", ".",
     "This", "is", "a", "second", "line", "."},
    {"A", "B", "C"},
    {"A.", "B"},
    { "皇后", "後世", "and", "(800)\u00A0326-1456" },
  };

  public static final String[][] RESULTS_EOL = {
    {"This", "is", "a", "test", ".", "*NL*",
     "This", "is", "a", "second", "line", "."},
    {"A", "*NL*", "B", "*NL*", "*NL*", "C"},
    {"A.", "B"},
    { "皇后", "後世", "and", "(800)\u00A0326-1456" },
  };

  public static final String[] TEST_WHITESPACE_ONLY = {
    "        ",
    "",
  };

  public static final String[] TEST_WHITESPACE_ONLY_EOL = {
    "\n\n\n",
  };

  public static final String[] TEST_NO_WHITESPACE = {
    "Thisisatest.Thisisasecondline.",
    "ABC",
    "A.B",
    "皇后30003000後世and(800)00A0326-1456",
  };

  private static void runTest(TokenizerFactory<? extends HasWord> factory,
                              String[] testStrings, String[][] resultsStrings) {
    for (int i = 0; i < testStrings.length; ++i) {
      Tokenizer<? extends HasWord> tokenizer =
        factory.getTokenizer(new StringReader(testStrings[i]));
      List<? extends HasWord> tokens = tokenizer.tokenize();
      assertEquals(resultsStrings[i].length, tokens.size());
      for (int j = 0; j < resultsStrings[i].length; ++j) {
        assertEquals(resultsStrings[i][j], tokens.get(j).word());
      }
    }
  }

  public void testWordTokenizer() {
    runTest(WhitespaceTokenizer.factory(false), TEST, RESULTS_NO_EOL);
    runTest(WhitespaceTokenizer.factory(true), TEST, RESULTS_EOL);
  }

  public void testCoreLabelTokenizer() {
    LexedTokenFactory<CoreLabel> factory = new CoreLabelTokenFactory();
    runTest(new WhitespaceTokenizer.WhitespaceTokenizerFactory<CoreLabel>
              (factory, false),
            TEST, RESULTS_NO_EOL);
    runTest(new WhitespaceTokenizer.WhitespaceTokenizerFactory<CoreLabel>
              (factory, true),
            TEST, RESULTS_EOL);
  }

  /* Tests the "whitespace only" bound */
  public void testNoTextWhiteSpaceTokenizer() {
    for (int i = 0; i < TEST_WHITESPACE_ONLY.length; ++i) {
      Tokenizer<? extends HasWord> tokenizer =
        WhitespaceTokenizer.factory(true).getTokenizer(new StringReader(TEST_WHITESPACE_ONLY[i]));
      List<? extends HasWord> tokens = tokenizer.tokenize();
      int expectedNumberOfActualTokens = 0;
      assertEquals(expectedNumberOfActualTokens, tokens.size());
    }
  }

  public void testPureWhiteSpaceEOLTokenizer() {
    for (int i = 0; i < TEST_WHITESPACE_ONLY_EOL.length; ++i) {
      Tokenizer<? extends HasWord> tokenizer =
        WhitespaceTokenizer.factory(false).getTokenizer(new StringReader(TEST_WHITESPACE_ONLY_EOL[i]));
      List<? extends HasWord> tokens = tokenizer.tokenize();
      int expectedNumberOfActualTokens = 0;
      assertEquals(expectedNumberOfActualTokens, tokens.size());
    }
  }

  /* Tests the "no whitespace at all" bound */
  public void testNoWhiteSpaceTokenizer() {
    for (int i = 0; i < TEST_NO_WHITESPACE.length; ++i) {
      Tokenizer<? extends HasWord> tokenizer =
        WhitespaceTokenizer.factory(true).getTokenizer(new StringReader(TEST_NO_WHITESPACE[i]));
      List<? extends HasWord> tokens = tokenizer.tokenize();
      int expectedNumberOfActualTokens = 1;
      assertEquals(expectedNumberOfActualTokens, tokens.size());
      assertEquals(TEST_NO_WHITESPACE[i], tokens.get(0).word());
    }
  }

}
