package edu.stanford.nlp.paragraphs;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.logging.Redwood;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Grace Muzny
 */
public class ParagraphAnnotator implements Annotator {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(ParagraphAnnotator.class);

  private final boolean VERBOSE;
  private final boolean DEBUG = true;

  // Whether or not to allow quotes of the same type embedded inside of each other
  // ["one" | "two"]
  public String PARAGRAPH_BREAK = "two";

  public ParagraphAnnotator(Properties props, boolean verbose) {
    PARAGRAPH_BREAK = props.getProperty("paragraphBreak", "two");
    VERBOSE = verbose;
  }

  @Override
  public void annotate(Annotation annotation) {
    if (VERBOSE) {
      System.err.print("Adding paragraph index annotation (" + PARAGRAPH_BREAK + ") ...");
    }
    Pattern paragraphSplit = null;
    if (PARAGRAPH_BREAK.equals("two")) {
      paragraphSplit = Pattern.compile("\\n\\n+");
    } else if (PARAGRAPH_BREAK.equals("one")) {
      paragraphSplit = Pattern.compile("\\n+");
    }

    String fullText = annotation.get(CoreAnnotations.TextAnnotation.class);
    Matcher m = paragraphSplit.matcher(fullText);
    List<Integer> paragraphBreaks = Generics.newArrayList();
    while (m.find()) {
      // get the staring index
      paragraphBreaks.add(m.start());
    }

    // each sentence gets a paragraph id annotation
    List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
    int currParagraph = -1;
    int nextParagraphStartIndex = -1;
    for (CoreMap sent : sentences) {
      int sentBegin = sent.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
      if (sentBegin >= nextParagraphStartIndex) {
        if (currParagraph + 1 < paragraphBreaks.size()) {
          nextParagraphStartIndex = paragraphBreaks.get(currParagraph + 1);
        } else {
          nextParagraphStartIndex = fullText.length();
        }
        currParagraph++;
      }
      sent.set(CoreAnnotations.ParagraphIndexAnnotation.class, currParagraph);
    }
    if (VERBOSE) {
      System.err.println("done");
    }
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return Collections.singleton(CoreAnnotations.ParagraphIndexAnnotation.class);
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return new HashSet<>(Arrays.asList(
        CoreAnnotations.TextAnnotation.class,
        CoreAnnotations.TokensAnnotation.class,
        CoreAnnotations.SentencesAnnotation.class,
        CoreAnnotations.CharacterOffsetBeginAnnotation.class,
        CoreAnnotations.CharacterOffsetEndAnnotation.class,
        CoreAnnotations.BeforeAnnotation.class,
        CoreAnnotations.AfterAnnotation.class,
        CoreAnnotations.TokenBeginAnnotation.class,
        CoreAnnotations.TokenEndAnnotation.class,
        CoreAnnotations.IndexAnnotation.class,
        CoreAnnotations.OriginalTextAnnotation.class
    ));
  }

}
