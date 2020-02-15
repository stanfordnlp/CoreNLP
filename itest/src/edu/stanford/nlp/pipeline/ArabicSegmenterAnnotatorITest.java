package edu.stanford.nlp.pipeline;

import junit.framework.TestCase;

import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;

public class ArabicSegmenterAnnotatorITest extends TestCase {
  StanfordCoreNLP pipeline = null;

  @Override
  public void setUp()
    throws Exception
  {
    if (pipeline != null) {
      return;
    }
    Properties props = new Properties();
    props.setProperty("annotators", "segment");
    props.setProperty("customAnnotatorClass.segment", "edu.stanford.nlp.pipeline.ArabicSegmenterAnnotator");
    props.setProperty("segment.model", "/u/nlp/data/arabic-segmenter/arabic-segmenter-atb+bn+arztrain.ser.gz");
    pipeline = new StanfordCoreNLP(props);
  }

  public void testPipeline() {
    String query = "وما هي كلمتُك المفضلة للدراسة؟";
    String[] expectedWords = {"و", "ما", "هي", "كلمة", "ك", "المفضلة", "ل", "الدراسة", "?"};
    int[] expectedStartPositions = {0, 1, 4, 7, 12, 14, 22, 23, 29};
    int[] expectedEndPositions = {1, 3, 6, 11, 13, 21, 23, 29, 30};
    Annotation annotation = new Annotation(query);
    pipeline.annotate(annotation);

    List<CoreLabel> tokens = annotation.get(TokensAnnotation.class);
    assertEquals(expectedWords.length, tokens.size());
    for (int i = 0; i < expectedWords.length; ++i) {
      assertEquals(expectedWords[i], tokens.get(i).word());
      assertEquals(expectedStartPositions[i], tokens.get(i).beginPosition());
      assertEquals(expectedEndPositions[i], tokens.get(i).endPosition());
    }
  }
}