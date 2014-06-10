package edu.stanford.nlp.process;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.pipeline.PTBTokenizerAnnotator;

import java.util.List;

import junit.framework.TestCase;


public class WordToSentenceProcessorTest extends TestCase {

  private static final Annotator ptb = new PTBTokenizerAnnotator(false);
  private static final WordToSentenceProcessor<CoreLabel> wts = new WordToSentenceProcessor<CoreLabel>();


  public static void checkResult(String testSentence,
                          String ... gold) {
    Annotation annotation = new Annotation(testSentence);
    ptb.annotate(annotation);
    List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
    List<List<CoreLabel>> sentences = wts.process(tokens);

    assertEquals(gold.length, sentences.size());

    Annotation[] goldAnnotations = new Annotation[gold.length];
    for (int i = 0; i < gold.length; ++i) {
      goldAnnotations[i] = new Annotation(gold[i]);
      ptb.annotate(goldAnnotations[i]);
      List<CoreLabel> goldTokens =
        goldAnnotations[i].get(CoreAnnotations.TokensAnnotation.class);
      List<CoreLabel> testTokens = sentences.get(i);
      int goldTokensSize = goldTokens.size();
      assertEquals(goldTokensSize, testTokens.size());
      for (int j = 0; j < goldTokensSize; ++j) {
        assertEquals(goldTokens.get(j).word(), testTokens.get(j).word());
      }
    }
  }

  public void testNoSplitting() {
    checkResult("This should only be one sentence.",
                "This should only be one sentence.");
  }

  public void testTwoSentences() {
    checkResult("This should be two sentences.  There is a split.",
                "This should be two sentences.",
                "There is a split.");
    checkResult("This should be two sentences!  There is a split.",
                "This should be two sentences!",
                "There is a split.");
    checkResult("This should be two sentences?  There is a split.",
                "This should be two sentences?",
                "There is a split.");
    checkResult("This should be two sentences!!!?!!  There is a split.",
                "This should be two sentences!!!?!!",
                "There is a split.");
  }

  public void testEdgeCases() {
    checkResult("This should be two sentences.  Second one incomplete",
                "This should be two sentences.",
                "Second one incomplete");
    checkResult("One incomplete sentence",
                "One incomplete sentence");
    checkResult("(Break after a parenthesis.)  (Or after \"quoted stuff!\")",
                "(Break after a parenthesis.)",
                "(Or after \"quoted stuff!\")");
    checkResult("  ");
  }

  public void testMr() {
    checkResult("Mr. White got a loaf of bread",
                "Mr. White got a loaf of bread");
  }

  /*
  public void testExclamationPoint() {
    Annotation annotation = new Annotation("Foo!!");
    ptb.annotate(annotation);
    System.out.println();
    System.out.println(annotation.get(CoreAnnotations.TokensAnnotation.class));
  }
  */
}
