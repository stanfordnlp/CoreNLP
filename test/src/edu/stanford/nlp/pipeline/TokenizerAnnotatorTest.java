package edu.stanford.nlp.pipeline;

import java.util.*;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import junit.framework.TestCase;


/** @author Christopher Manning */
public class TokenizerAnnotatorTest extends TestCase {

  private static final String text = "She'll prove it ain't so.";
  private static List<String> tokenWords = Arrays.asList(new String[] {
          "She",
          "'ll",
          "prove",
          "it",
          "ai",
          "n't",
          "so",
          "."
  });

  public void testNewVersion() {
    Annotation ann = new Annotation(text);
    Annotator annotator = new TokenizerAnnotator("en");
    annotator.annotate(ann);
    Iterator<String> it = tokenWords.iterator();
    for (CoreLabel word : ann.get(CoreAnnotations.TokensAnnotation.class)) {
      assertEquals("Bung token in new CoreLabel usage", it.next(), word.word());
    }
    assertFalse("Too few tokens in new CoreLabel usage", it.hasNext());

    Iterator<String> it2 = tokenWords.iterator();
    for (CoreLabel word : ann.get(CoreAnnotations.TokensAnnotation.class)) {
      assertEquals("Bung token in new CoreLabel usage", it2.next(), word.get(CoreAnnotations.TextAnnotation.class));
    }
    assertFalse("Too few tokens in new CoreLabel usage", it2.hasNext());
  }

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

  public void testBadLanguage() {
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize");
    props.setProperty("tokenize.language", "notalanguage");
    try {
      StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
      throw new RuntimeException("Should have failed");
    } catch (IllegalArgumentException e) {
      // yay, passed
    }
  }

  private static final String spanishText = "Me voy a Madrid (ES).\n\"Me gusta\", lo dice.";
  private static List<String> spanishTokens = Arrays.asList(new String[] { "Me", "voy", "a", "Madrid", "=LRB=", "ES", "=RRB=", ".", "\"", "Me", "gusta", "\"", ",", "lo", "dice", "." });
  private static final String spanishText2 = "Me voy a Madrid (ES).\n(Me gusta), lo dice.";
  private static List<String> spanishTokens2 = Arrays.asList(new String[] { "Me", "voy", "a", "Madrid", "=LRB=", "ES", "=RRB=", ".", "*NL*", "\"", "Me", "gusta", "\"", ",", "lo", "dice", "." });

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
