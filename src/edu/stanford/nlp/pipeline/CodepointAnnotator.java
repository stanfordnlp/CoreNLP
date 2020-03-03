package edu.stanford.nlp.pipeline;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.logging.Redwood;


/**
 * Annotator to add codepoint counts to tokens
 * <br>
 * In general this will be the same as the character offsets, but
 * certain fancy characters such as 𝒚̂𝒊 will change that.
 */
public class CodepointAnnotator implements Annotator {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(CodepointAnnotator.class);

  public CodepointAnnotator(Properties properties) { }
  public CodepointAnnotator() {}

  private static int getTextCodepoints(CoreLabel label, Class<? extends CoreAnnotation<String>> annotation) {
    String text = label.get(annotation);
    return Character.codePointCount(text, 0, text.length());
  }

  @Override
  public void annotate(Annotation annotation) {
    int current = 0;
    List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
    for (CoreLabel label : tokens) {
      current = current + getTextCodepoints(label, CoreAnnotations.BeforeAnnotation.class);
      label.set(CoreAnnotations.CodepointOffsetBeginAnnotation.class, current);

      current = current + getTextCodepoints(label, CoreAnnotations.OriginalTextAnnotation.class);
      label.set(CoreAnnotations.CodepointOffsetEndAnnotation.class, current);
    }    
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
        CoreAnnotations.TokensAnnotation.class,
        CoreAnnotations.BeforeAnnotation.class,
        CoreAnnotations.AfterAnnotation.class,
        CoreAnnotations.OriginalTextAnnotation.class)));
  }


  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
        CoreAnnotations.CodepointOffsetBeginAnnotation.class,
        CoreAnnotations.CodepointOffsetEndAnnotation.class)));
  }


}
