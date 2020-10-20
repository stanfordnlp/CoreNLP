package edu.stanford.nlp.international.spanish;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.junit.Test;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;

/**
 * @see edu.stanford.nlp.pipeline.TokenizerAnnotatorTest
 *
 * But, for Spanish (an itest because it relies on a model)
 *
 * @author Gabor Angeli
 */
public class SpanishTokenizerAnnotatorITest {

  private static List<String> spanishTokens = Arrays.asList(
      "Damelo");

  @Test
  public void testSpanish() {
    Annotation ann = new Annotation("Damelo");
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize");
    props.setProperty("tokenize.language", "es");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    pipeline.annotate(ann);

    Iterator<String> it = spanishTokens.iterator();
    for (CoreLabel word : ann.get(CoreAnnotations.TokensAnnotation.class)) {
      assertEquals("Bung token in new CoreLabel usage", it.next(), word.get(CoreAnnotations.TextAnnotation.class));
    }
    assertFalse("Too few tokens in new CoreLabel usage", it.hasNext());
  }
}
