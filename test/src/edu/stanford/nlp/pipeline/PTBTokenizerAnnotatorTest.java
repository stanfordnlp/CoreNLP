package edu.stanford.nlp.pipeline;

import java.util.*;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import junit.framework.TestCase;


/** @author Christopher Manning */
public class PTBTokenizerAnnotatorTest extends TestCase {

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
    Annotator annotator = new PTBTokenizerAnnotator();
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

}
