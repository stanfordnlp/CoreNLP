package edu.stanford.nlp.process;

import java.util.List;
import java.util.Properties;

import junit.framework.TestCase;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.pipeline.TokenizerAnnotator;



public class CodepointCoreLabelProcessorTest extends TestCase {

  private static final String basicText = "She'll prove it ain't so.\n\nMaybe it is\n";

  private static final int[] basicBegin = { 0, 3,  7, 13, 16, 18, 22, 24, 27, 33, 36 };
  private static final int[] basicEnd   = { 3, 6, 12, 15, 18, 21, 24, 25, 32, 35, 38 };

  private Annotation annotate(String text) {
    Annotation ann = new Annotation(text);

    Properties props = new Properties();
    props.setProperty("tokenize.language", "en");
    props.setProperty("tokenize.codepoint", "true");
    Annotator tokenizer = new TokenizerAnnotator(false, props, "");

    tokenizer.annotate(ann);
    return ann;
  }

  private void checkAnnotation(Annotation ann, int[] expectedBegin, int[] expectedEnd, boolean charOffsets) {
    List<CoreLabel> tokens = ann.get(CoreAnnotations.TokensAnnotation.class);
    assertEquals(tokens.size(), expectedBegin.length);
    assertEquals(tokens.size(), expectedEnd.length);
    for (int i = 0; i < tokens.size(); ++i) {
      CoreLabel token = tokens.get(i);

      int begin = token.get(CoreAnnotations.CodepointOffsetBeginAnnotation.class);
      assertEquals(expectedBegin[i], begin);
      if (charOffsets) {
        int cBegin = token.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
        assertEquals(expectedBegin[i], cBegin);
      }

      int end = token.get(CoreAnnotations.CodepointOffsetEndAnnotation.class);
      assertEquals(expectedEnd[i], end);
      if (charOffsets) {
        int cEnd = token.get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
        assertEquals(expectedEnd[i], cEnd);
      }
    }
  }

  /**
   * Offsets should basically be the same for characters and codepoints
   */
  public void testBasic() {
    Annotation ann = annotate(basicText);
    checkAnnotation(ann, basicBegin, basicEnd, true);
  }

  private static final String funnyText = "I am 𝒚̂𝒊 random text";
  private static final int[] funnyBegin = { 0, 2, 5,  9, 16 };
  private static final int[] funnyEnd   = { 1, 4, 8, 15, 20 };

  /**
   * This whole annotator is because of a user submitted complaint in
   * which the values for offset begin/end weren't useful because the
   * non-basic characters added extra offset
   */
  public void testFunny() {
    Annotation ann = annotate(funnyText);
    checkAnnotation(ann, funnyBegin, funnyEnd, false);
  }
}
