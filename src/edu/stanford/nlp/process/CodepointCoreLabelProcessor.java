package edu.stanford.nlp.process;

import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;

/**
 * Processor to add codepoint counts to tokens
 * <br>
 * In general this will be the same as the character offsets, but
 * certain fancy characters such as 𝒚̂𝒊 will change that.
 *
 * @author John Bauer
 */
public class CodepointCoreLabelProcessor extends CoreLabelProcessor {
  private static int getTextCodepoints(CoreLabel label, Class<? extends CoreAnnotation<String>> annotation) {
    String text = label.get(annotation);
    return Character.codePointCount(text, 0, text.length());
  }

  /**
   * Adds codepoint offsets to the tokens (parallel to character offsets).
   * Does so in-place and returns the original tokens.
   */
  @Override
  public List<CoreLabel> process(List<CoreLabel> tokens) {
    int current = 0;
    for (CoreLabel label : tokens) {
      current = current + getTextCodepoints(label, CoreAnnotations.BeforeAnnotation.class);
      label.set(CoreAnnotations.CodepointOffsetBeginAnnotation.class, current);

      current = current + getTextCodepoints(label, CoreAnnotations.OriginalTextAnnotation.class);
      label.set(CoreAnnotations.CodepointOffsetEndAnnotation.class, current);
    }
    return tokens;
  }

  /**
   * Removes the codepoint offsets in-place and returns the deannotated tokens
   */
  @Override
  public List<CoreLabel> restore(List<CoreLabel> originalTokens, List<CoreLabel> processedTokens) {
    for (CoreLabel label : processedTokens) {
      label.remove(CoreAnnotations.CodepointOffsetBeginAnnotation.class);
      label.remove(CoreAnnotations.CodepointOffsetEndAnnotation.class);
    }
    return processedTokens;
  }
}
