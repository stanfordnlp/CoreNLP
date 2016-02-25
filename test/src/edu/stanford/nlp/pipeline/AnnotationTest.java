package edu.stanford.nlp.pipeline;

import junit.framework.TestCase;

import java.util.List;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;

/**
 * @author John Bauer
 */
public class AnnotationTest extends TestCase {
  /** 
   * Test a bug a user reported where the text would wind up having the list toString used, adding extra []
   */
  public void testFromList() {
    List<CoreMap> sentences = Generics.newArrayList();

    CoreMap sentence = new ArrayCoreMap();
    List<CoreLabel> words = SentenceUtils.toCoreLabelList("This", "is", "a", "test", ".");
    sentence.set(CoreAnnotations.TokensAnnotation.class, words);
    sentences.add(sentence);

    Annotation annotation = new Annotation(sentences);
    assertEquals("This is a test .", annotation.toString());

    sentence.set(CoreAnnotations.TextAnnotation.class, "This is a test.");
    annotation = new Annotation(sentences);
    assertEquals("This is a test.", annotation.toString());
  }
}
