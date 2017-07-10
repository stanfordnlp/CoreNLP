package edu.stanford.nlp.pipeline;

import java.util.*;

import junit.framework.TestCase;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.AbstractTokenizer;


/**
 * Tests a couple tokenizer options, such as working with Spanish.
 * See TokenizerAnnotatorTest for more tests.
 *
 * @author John Bauer
 */
public class TokenizerAnnotatorITest extends TestCase {

  public void testNotSpanish() {
    Annotation ann = new Annotation("Damelo");
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize");
    props.setProperty("tokenize.language", "english");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    pipeline.annotate(ann);

    assertEquals(1, ann.get(CoreAnnotations.TokensAnnotation.class).size());
    assertEquals("Damelo", ann.get(CoreAnnotations.TokensAnnotation.class).get(0).word());
  }

  private static final String spanishText = "Me voy a Madrid (ES).\n\"Me gusta\", lo dice.";
  private static List<String> spanishTokens = Arrays.asList(new String[] { "Me", "voy", "a", "Madrid", "=LRB=", "ES", "=RRB=", ".", "\"", "Me", "gusta", "\"", ",", "lo", "dice", "." });
  private static List<String> spanishTokens2 = Arrays.asList(new String[] { "Me", "voy", "a", "Madrid", "=LRB=", "ES", "=RRB=", ".", AbstractTokenizer.NEWLINE_TOKEN, "\"", "Me", "gusta", "\"", ",", "lo", "dice", "." });

  public void testSpanishTokenizer() {
    TokenizerAnnotator annotator = new TokenizerAnnotator(false, "es", null);
    Annotation annotation = new Annotation(spanishText);
    annotator.annotate(annotation);
    List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
    assertEquals(spanishTokens.size(), tokens.size());
    for (int i = 0; i < tokens.size(); ++i) {
      assertEquals(spanishTokens.get(i), tokens.get(i).value());
    }

    annotator = new TokenizerAnnotator(false, "es", "tokenizeNLs,");
    annotation = new Annotation(spanishText);
    annotator.annotate(annotation);
    tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
    assertEquals(spanishTokens2.size(), tokens.size());
    for (int i = 0; i < tokens.size(); ++i) {
      assertEquals(spanishTokens2.get(i), tokens.get(i).value());
    }
  }

}
