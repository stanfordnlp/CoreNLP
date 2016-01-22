package edu.stanford.nlp.pipeline;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;

/**
 * @author John Bauer
 */

public class CleanXmlAnnotatorTest extends TestCase {

  private static Annotator ptbInvertible = null;
  private static Annotator ptbNotInvertible = null;

  private static Annotator cleanXmlAllTags = null;
  private static Annotator cleanXmlSomeTags = null;
  private static Annotator cleanXmlEndSentences = null;
  private static Annotator cleanXmlWithFlaws = null;

  private static Annotator wtsSplitter = null;

  /**
   * Initialize the annotators at the start of the unit test.
   * If they've already been initialized, do nothing.
   */
  @Override
  public void setUp() {
    synchronized(CleanXmlAnnotatorTest.class) {
      if (ptbInvertible == null) {
        ptbInvertible =
          new TokenizerAnnotator(false, "en", "invertible,ptb3Escaping=true");
      }
      if (ptbNotInvertible == null) {
        ptbNotInvertible =
          new TokenizerAnnotator(false, "en",
                                    "invertible=false,ptb3Escaping=true");
      }
      if (cleanXmlAllTags == null) {
        cleanXmlAllTags = new CleanXmlAnnotator(".*", "", "", false);
      }
      if (cleanXmlSomeTags == null) {
        cleanXmlSomeTags = new CleanXmlAnnotator("p", "", "", false);
      }
      if (cleanXmlEndSentences == null) {
        cleanXmlEndSentences = new CleanXmlAnnotator(".*", "p", "", false);
      }
      if (cleanXmlWithFlaws == null) {
        cleanXmlWithFlaws = new CleanXmlAnnotator(".*", "", "", true);
      }
      if (wtsSplitter == null) {
        wtsSplitter = new WordsToSentencesAnnotator(false);
      }
    }
  }

  public static Annotation annotate(String text,
                                    Annotator tokenizer, Annotator xmlRemover,
                                    Annotator splitter) {
    Annotation annotation = new Annotation(text);
    tokenizer.annotate(annotation);
    if (xmlRemover != null)
      xmlRemover.annotate(annotation);
    if (splitter != null)
      splitter.annotate(annotation);
    return annotation;
  }

  public static void checkResult(Annotation annotation,
                                 String... gold) {
    List<CoreLabel> goldTokens = new ArrayList<CoreLabel>();
    Annotation[] goldAnnotations = new Annotation[gold.length];
    for (int i = 0; i < gold.length; ++i) {
      goldAnnotations[i] = annotate(gold[i], ptbInvertible, null, null);
      goldTokens.addAll(goldAnnotations[i].get(CoreAnnotations.TokensAnnotation.class));
    }
    List<CoreLabel> annotationLabels = annotation.get(CoreAnnotations.TokensAnnotation.class);

    if (goldTokens.size() != annotationLabels.size()) {
      for (CoreLabel annotationLabel : annotationLabels) {
        System.err.print(annotationLabel.word() + " ");
      }
      System.err.println();
      for (CoreLabel goldToken : goldTokens) {
        System.err.print(goldToken.word() + " ");
      }
      System.err.println();
    }

    assertEquals(goldTokens.size(), annotationLabels.size());
    for (int i = 0; i < annotationLabels.size(); ++i) {
      assertEquals(goldTokens.get(i).word(),
                   annotationLabels.get(i).word());
    }

    if (annotation.get(CoreAnnotations.SentencesAnnotation.class) != null) {
      List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
      assertEquals(gold.length, sentences.size());
    }
  }

  public static void checkInvert(Annotation annotation, String gold) {
    List<CoreLabel> annotationLabels =
      annotation.get(CoreAnnotations.TokensAnnotation.class);
    StringBuilder original = new StringBuilder();
    for (CoreLabel label : annotationLabels) {
      original.append(label.get(CoreAnnotations.BeforeAnnotation.class));
      original.append(label.get(CoreAnnotations.OriginalTextAnnotation.class));
    }
    original.append(annotationLabels.get(annotationLabels.size() - 1).
                    get(CoreAnnotations.AfterAnnotation.class));
    assertEquals(gold, original.toString());
  }

  public static void checkContext(CoreLabel label, String... expectedContext) {
    List<String> xmlContext = label.get(CoreAnnotations.XmlContextAnnotation.class);
    assertEquals(expectedContext.length, xmlContext.size());
    for (int i = 0; i < expectedContext.length; ++i) {
      assertEquals(expectedContext[i], xmlContext.get(i));
    }
  }

  public void testRemoveXML() {
    String testString = "<xml>This is a test string.</xml>";
    checkResult(annotate(testString, ptbInvertible,
                         cleanXmlAllTags, wtsSplitter),
                "This is a test string.");
  }

  public void testExtractSpecificTag() {
    String testString = ("<p>This is a test string.</p>" +
                         "<foo>This should not be found</foo>");
    checkResult(annotate(testString, ptbInvertible,
                         cleanXmlSomeTags, wtsSplitter),
                "This is a test string.");
  }

  public void testSentenceSplitting() {
    String testString = ("<p>This sentence is split</p>" +
                         "<foo>over two tags</foo>");
    checkResult(annotate(testString, ptbInvertible,
                         cleanXmlAllTags, wtsSplitter),
                "This sentence is split over two tags");
    checkResult(annotate(testString, ptbInvertible,
                         cleanXmlEndSentences, wtsSplitter),
                "This sentence is split", "over two tags");
  }

  public void testNestedTags() {
    String testString = "<p><p>This text is in a</p>nested tag</p>";
    checkResult(annotate(testString, ptbInvertible,
                         cleanXmlAllTags, wtsSplitter),
                "This text is in a nested tag");
    checkResult(annotate(testString, ptbInvertible,
                         cleanXmlEndSentences, wtsSplitter),
                "This text is in a", "nested tag");
  }

  public void testMissingCloseTags() {
    String testString = "<text><p>This text <p>has closing tags wrong</text>";
    checkResult(annotate(testString, ptbInvertible,
                         cleanXmlWithFlaws, wtsSplitter),
                "This text has closing tags wrong");
    try {
      checkResult(annotate(testString, ptbInvertible,
                           cleanXmlAllTags, wtsSplitter),
                  "This text has closing tags wrong");
      throw new RuntimeException("it was supposed to barf");
    } catch(IllegalArgumentException e) {
      // this is what was supposed to happen
    }
  }

  public void testEarlyEnd() {
    String testString = "<text>This text ends before all tags closed";
    checkResult(annotate(testString, ptbInvertible,
                         cleanXmlWithFlaws, wtsSplitter),
                "This text ends before all tags closed");
    try {
      checkResult(annotate(testString, ptbInvertible,
                           cleanXmlAllTags, wtsSplitter),
                  "This text ends before all tags closed");
      throw new RuntimeException("it was supposed to barf");
    } catch(IllegalArgumentException e) {
      // this is what was supposed to happen
    }
  }

  public void testInvertible() {
    String testNoTags = "This sentence should be invertible.";
    String testTags =
      "  <xml>  This sentence should  be  invertible.  </xml>  ";
    String testManyTags =
      " <xml>   <foo>       <bar>This sentence should  " +
      "   </bar>be invertible.   </foo>   </xml> ";

    Annotation annotation = annotate(testNoTags, ptbInvertible,
                                     cleanXmlAllTags, wtsSplitter);
    checkResult(annotation, testNoTags);
    checkInvert(annotation, testNoTags);

    annotation = annotate(testTags, ptbInvertible,
                          cleanXmlAllTags, wtsSplitter);
    checkResult(annotation, testNoTags);
    checkInvert(annotation, testTags);

    annotation = annotate(testManyTags, ptbInvertible,
                          cleanXmlAllTags, wtsSplitter);
    checkResult(annotation, testNoTags);
    checkInvert(annotation, testManyTags);
  }

  public void testContext() {
    String testManyTags =
      " <xml>   <foo>       <bar>This sentence should  " +
      "   </bar>be invertible.   </foo>   </xml> ";
    Annotation annotation = annotate(testManyTags, ptbInvertible,
                                     cleanXmlAllTags, wtsSplitter);

    List<CoreLabel> annotationLabels =
      annotation.get(CoreAnnotations.TokensAnnotation.class);
    for (int i = 0; i < 3; ++i) {
      checkContext(annotationLabels.get(i), "xml", "foo", "bar");
    }
    for (int i = 3; i < 5; ++i) {
      checkContext(annotationLabels.get(i), "xml", "foo");
    }
  }

  public void testOffsets() {
    String testString = "<p><p>This text is in a</p>nested tag</p>";
    Annotation annotation = annotate(testString, ptbInvertible,
                                     cleanXmlAllTags, wtsSplitter);
    checkResult(annotation, "This text is in a nested tag");
    List<CoreLabel> labels = annotation.get(CoreAnnotations.TokensAnnotation.class);
    assertEquals(6,
                 labels.get(0).
                 get(CoreAnnotations.CharacterOffsetBeginAnnotation.class).intValue());
    assertEquals(10,
                 labels.get(0).
                 get(CoreAnnotations.CharacterOffsetEndAnnotation.class).intValue());
  }

  public void testAttributes() {
    String testString = "<p a=\"b\">This text has an attribute</p>";
    Annotation annotation = annotate(testString, ptbInvertible,
                                     cleanXmlAllTags, wtsSplitter);
    checkResult(annotation, "This text has an attribute");
  }
}
