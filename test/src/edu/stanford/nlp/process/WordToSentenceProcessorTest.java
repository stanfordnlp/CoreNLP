package edu.stanford.nlp.process;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.pipeline.TokenizerAnnotator;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;
import junit.framework.TestCase;


public class WordToSentenceProcessorTest extends TestCase {

  private static final TokenizerAnnotator onelineTokenizer =
    new TokenizerAnnotator(false, PropertiesUtils.asProperties("tokenize.language", "en", "tokenize.ssplit", "false"), null);
  private static final TokenizerAnnotator udNL =
    new TokenizerAnnotator(false, PropertiesUtils.asProperties("tokenize.language", "en", "tokenize.ssplit", "false"), "invertible,tokenizeNLs=true");
  private static final TokenizerAnnotator wsNL =
    new TokenizerAnnotator(false, PropertiesUtils.asProperties("tokenize.whitespace", "true", "invertible", "true", "tokenizeNLs", "true"));

  private static final WordToSentenceProcessor<CoreLabel> wts = new WordToSentenceProcessor<>();
  private static final WordToSentenceProcessor<CoreLabel> wtsNull =
    new WordToSentenceProcessor<>(true); // treat input as one sentence
  private static final WordToSentenceProcessor<CoreLabel> cwts =
    new WordToSentenceProcessor<>("[.。]|[!?！？]+", WordToSentenceProcessor.NewlineIsSentenceBreak.TWO_CONSECUTIVE, false);


  private static void checkResult(WordToSentenceProcessor<CoreLabel> wts,
                                  String testSentence, String... gold) {
    checkResult(wts, onelineTokenizer, testSentence, gold);
  }

  private static void checkResult(WordToSentenceProcessor<CoreLabel> wts,
                                  TokenizerAnnotator tokenizer,
                                  String testSentence, String ... gold) {
    Annotation annotation = new Annotation(testSentence);
    udNL.annotate(annotation);
    List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
    List<List<CoreLabel>> sentences = wts.process(tokens);

    assertEquals("Output number of sentences didn't match:\n" + Arrays.toString(gold) + " vs. \n" + sentences + '\n',
                 gold.length, sentences.size());

    Annotation[] goldAnnotations = new Annotation[gold.length];
    for (int i = 0; i < gold.length; ++i) {
      goldAnnotations[i] = new Annotation(gold[i]);
      tokenizer.annotate(goldAnnotations[i]);
      List<CoreLabel> goldTokens = goldAnnotations[i].get(CoreAnnotations.TokensAnnotation.class);
      List<CoreLabel> testTokens = sentences.get(i);
      int goldTokensSize = goldTokens.size();
      assertEquals("Sentence lengths didn't match:\n" + goldTokens + " vs. \n" + testTokens + '\n',
                   goldTokensSize, testTokens.size());
      for (int j = 0; j < goldTokensSize; ++j) {
        assertEquals(goldTokens.get(j).word(), testTokens.get(j).word());
      }
    }
  }

  public void testNoSplitting() {
    checkResult(wts, "This should only be one sentence.",
                "This should only be one sentence.");
  }

  public void testTwoSentences() {
    checkResult(wts, "This should be two sentences.  There is a split.",
                "This should be two sentences.",
                "There is a split.");
    checkResult(wts, "This should be two sentences!  There is a split.",
                "This should be two sentences!",
                "There is a split.");
    checkResult(wts, "This should be two sentences?  There is a split.",
                "This should be two sentences?",
                "There is a split.");
    checkResult(wts, "This should be two sentences!!!?!!  There is a split.",
                "This should be two sentences!!!?!!",
                "There is a split.");
  }

  public void testEdgeCases() {
    checkResult(wts, "This should be two sentences.  Second one incomplete",
                "This should be two sentences.",
                "Second one incomplete");
    checkResult(wts, "One incomplete sentence",
                "One incomplete sentence");
    checkResult(wts, "(Break after a parenthesis.)  (Or after \"quoted stuff!\")",
                "(Break after a parenthesis.)",
                "(Or after \"quoted stuff!\")");
    checkResult(wts, "  ");
    checkResult(wts, "This should be\n one sentence.",
                "This should be one sentence.");
    checkResult(wts, "'') Funny stuff joined on.",
                "'') Funny stuff joined on.");

  }

  public void testMr() {
    checkResult(wts, "Mr. White got a loaf of bread",
                "Mr. White got a loaf of bread");
  }

  public void testNullSplitter() {
    checkResult(wtsNull, "This should be one sentence.  There is no split.",
                "This should be one sentence.  There is no split.");
  }

  public void testParagraphStrategies() {
    final WordToSentenceProcessor<CoreLabel> wtsNever =
      new WordToSentenceProcessor<>(WordToSentenceProcessor.NewlineIsSentenceBreak.NEVER);
    final WordToSentenceProcessor<CoreLabel> wtsAlways =
      new WordToSentenceProcessor<>(WordToSentenceProcessor.NewlineIsSentenceBreak.ALWAYS);
    final WordToSentenceProcessor<CoreLabel> wtsTwo =
      new WordToSentenceProcessor<>(WordToSentenceProcessor.NewlineIsSentenceBreak.TWO_CONSECUTIVE);

    String input1 = "Depending on the options,\nthis could be all sorts of things,\n\n as I like chocolate. And cookies.";
    String input2 = "Depending on the options,\nthis could be all sorts of things,\n as I like chocolate. And cookies.";
    checkResult(wtsNever, input1,
                "Depending on the options,\nthis could be all sorts of things,\n\nas I like chocolate.",
                "And cookies.");
    checkResult(wtsAlways, input1,
                "Depending on the options,",
                "this could be all sorts of things,",
                "as I like chocolate.",
                "And cookies.");
    checkResult(wtsTwo, input1,
                "Depending on the options, this could be all sorts of things,",
                "as I like chocolate.",
                "And cookies.");
    checkResult(wtsNever, input2,
                "Depending on the options,\nthis could be all sorts of things,\nas I like chocolate.",
                "And cookies.");
    checkResult(wtsAlways, input2,
                "Depending on the options,",
                "this could be all sorts of things,",
                "as I like chocolate.",
                "And cookies.");
    checkResult(wtsTwo, input2,
                "Depending on the options,\nthis could be all sorts of things,\nas I like chocolate.",
                "And cookies.");
    String input3 = "Specific descriptions are absent.\n\n''Mossy Head Industrial Park'' it says.";
    checkResult(wtsTwo, input3,
                "Specific descriptions are absent.",
                "''Mossy Head Industrial Park'' it says.");
  }

  public void testXmlElements() {
    final WordToSentenceProcessor<CoreLabel> wtsXml =
      new WordToSentenceProcessor<>(null, null,null,
                                    Generics.newHashSet(Arrays.asList("p", "chapter")),
                                    WordToSentenceProcessor.NewlineIsSentenceBreak.NEVER, null, null);

    String input1 = "<chapter>Chapter 1</chapter><p>This is text. So is this.</p> <p>One without end</p><p>Another</p><p>And another</p>";
    checkResult(wtsXml, input1,
                "Chapter 1",
                "This is text.",
                "So is this.",
                "One without end",
                "Another",
                "And another");
  }

  public void testRegion() {
    final WordToSentenceProcessor<CoreLabel> wtsRegion =
      new WordToSentenceProcessor<>(WordToSentenceProcessor.DEFAULT_BOUNDARY_REGEX,
                                    WordToSentenceProcessor.DEFAULT_BOUNDARY_FOLLOWERS_REGEX,
                                    WordToSentenceProcessor.DEFAULT_SENTENCE_BOUNDARIES_TO_DISCARD,
                                    Generics.newHashSet(Collections.singletonList("p")),
                                    "chapter|preface", WordToSentenceProcessor.NewlineIsSentenceBreak.NEVER, null, null, false, false);
    String input1 = "<title>Chris rules!</title><preface><p>Para one</p><p>Para two</p></preface>" +
      "<chapter><p>Text we like. Two sentences \n\n in it.</p></chapter><coda>Some more text here</coda>";
    checkResult(wtsRegion, input1,
                "Para one",
                "Para two",
                "Text we like.",
                "Two sentences in it.");

  }

  public void testBlankLines() {
    final WordToSentenceProcessor<CoreLabel> wtsLines =
      new WordToSentenceProcessor<>(Generics.newHashSet(WordToSentenceProcessor.DEFAULT_SENTENCE_BOUNDARIES_TO_DISCARD));
    String input1 = "Depending on the options,\nthis could be all sorts of things,\n\n as I like chocolate. And cookies.";
    checkResult(wtsLines, input1,
                "Depending on the options,",
                "this could be all sorts of things,",
                "",
                "as I like chocolate. And cookies.");
    String input2 = "Depending on the options,\nthis could be all sorts of things,\n\n as I like chocolate. And cookies.\n";
    checkResult(wtsLines, input2,
                "Depending on the options,",
                "this could be all sorts of things,",
                "",
                "as I like chocolate. And cookies.");
    String input3 = "Depending on the options,\nthis could be all sorts of things,\n\n as I like chocolate. And cookies.\n\n";
    checkResult(wtsLines, input3,
                "Depending on the options,",
                "this could be all sorts of things,",
                "",
                "as I like chocolate. And cookies.",
                "");
  }

  public void testExclamationPoint() {
    Annotation annotation = new Annotation("Foo!!");
    onelineTokenizer.annotate(annotation);
    List<CoreLabel> list = annotation.get(CoreAnnotations.TokensAnnotation.class);
    assertEquals("Wrong double bang", "[Foo, !!]", list.toString());
  }

  public void testChinese() {
    checkResult(cwts,
                wsNL,
                "巴拉特 说 ： 「 我们 未 再 获得 任何 结果 。 」 ＜ 金融时报 ？ ＞ 《 金融时报 》 周三",
                "巴拉特 说 ： 「 我们 未 再 获得 任何 结果 。 」",
                "＜ 金融时报 ？ ＞",
                "《 金融时报 》 周三");

  }


  /**
   * Ensure that the unicode paragraph separator always
   * starts a new sentence.
   */
  public void testParagraphSeparator() {
    checkResult(wts, "Hello\u2029World.",
                "Hello", "World.");
    checkResult(wts, "Hello.\u2029World.",
                "Hello.", "World.");
    checkResult(wts, "Hello  \u2029World.",
                "Hello", "World.");
  }
}
