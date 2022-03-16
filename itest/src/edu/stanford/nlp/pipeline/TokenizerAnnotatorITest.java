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

  private static final String spanishText = "Me voy a Madrid (ES)\n\n\"Me gusta\", lo dice.";
  private static final String[] spanishTokens = { "Me", "voy", "a", "Madrid", "(", "ES", ")", "\"", "Me", "gusta", "\"", ",", "lo", "dice", "." };

  public void testSpanishTokenizer() {
    Properties props = new Properties();
    props.setProperty("tokenize.language", "es");

    TokenizerAnnotator annotator = new TokenizerAnnotator(false, props);
    Annotation annotation = new Annotation(spanishText);
    annotator.annotate(annotation);
    List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
    assertEquals(spanishTokens.length, tokens.size());
    for (int i = 0; i < tokens.size(); ++i) {
      assertEquals(spanishTokens[i], tokens.get(i).value());
    }
    assertEquals(1, annotation.get(CoreAnnotations.SentencesAnnotation.class).size());

    // the difference here with NEWLINE_... = two, tokenizeNLs is on
    // and there will be two sentences
    // the sentence splitter inside the TokenizerAnnotator will see
    // the *NL* and split a second sentence there
    props.setProperty(StanfordCoreNLP.NEWLINE_IS_SENTENCE_BREAK_PROPERTY, "two");

    annotator = new TokenizerAnnotator(false, props);
    annotation = new Annotation(spanishText);
    annotator.annotate(annotation);
    tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
    assertEquals(spanishTokens.length, tokens.size());
    for (int i = 0; i < tokens.size(); ++i) {
      assertEquals(spanishTokens[i], tokens.get(i).value());
    }
    assertEquals(2, annotation.get(CoreAnnotations.SentencesAnnotation.class).size());
  }

}
