package edu.stanford.nlp.ie;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.util.CoreMap;
import junit.framework.TestCase;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Test cases for number normalizer
 *
 * @author Angel Chang
 */
public class NumberNormalizerITest extends TestCase {

  static AnnotationPipeline pipeline = null;

  @Override
  public void setUp() throws Exception {
    synchronized(NumberNormalizerITest.class) {
      if (pipeline == null) {
        pipeline = new AnnotationPipeline();
        pipeline.addAnnotator(new PTBTokenizerAnnotator(false));
        pipeline.addAnnotator(new WordsToSentencesAnnotator(false));
        pipeline.addAnnotator(new POSTaggerAnnotator(DefaultPaths.DEFAULT_POS_MODEL, false));
      }
    }
  }

  public void testNumbers() throws IOException {
    // Set up test text
    String testText =
            "two dozen\n" +
            "six hundred,\n" +
            "four hundred, and twelve.\n" +
            "4 million six hundred fifty thousand, two hundred and eleven.\n" +
            "6 hundred billion, five million six hundred fifty thousand, three hundred and seventy six\n" +
            "5,786,345\n" +
            "twenty-five.\n" +

      //      "one and half million\n" +
            "1.3 million.\n" +
            "one thousand two hundred and twenty four\n" +
            "10 thousand million.\n"+
            "3.625\n" +

            "zero\n" +
            "-15\n" +
            "one two three four.\n" +
            "one hundred and fifty five\n" +
            "a hundred\n" 
//            "five oh four\n"
            ;

    // set up expected results
    Iterator<? extends Number> expectedNumbers = Arrays.asList(
            24.0, 600.0, 412.0, 4650211.0, 600005650376.0, 5786345, 25.0,
            1300000.0, 1224.0, 10000000000.0, 3.625,
            0, -15.0, 1, 2, 3, 4, 155.0, 100 /*504.0, */ ).iterator();
    Iterator<String> expectedTexts = Arrays.asList(
            "two dozen", "six hundred", "four hundred, and twelve",
            "4 million six hundred fifty thousand, two hundred and eleven",
            "6 hundred billion, five million six hundred fifty thousand, three hundred and seventy six",
            "5,786,345",
            "twenty-five",
      //      "one and half million\n" +
            "1.3 million",
            "one thousand two hundred and twenty four",
            "10 thousand million",
            "3.625",
            "zero", "-15", "one", "two", "three", "four",
            "one hundred and fifty five",
            "hundred" /* "five oh four", */ ).iterator();

    // create document
    Annotation document = createDocument(testText);

    // Annotate numbers
    NumberNormalizer.findAndAnnotateNumericExpressions(document);

    // Check answers
    for (CoreMap num: document.get(CoreAnnotations.NumerizedTokensAnnotation.class)) {
      if (num.has(CoreAnnotations.NumericCompositeTypeAnnotation.class)) {
        Number expectedNumber = expectedNumbers.next();
        String expectedType = "NUMBER";
        String expectedText = expectedTexts.next();
        String text = document.get(CoreAnnotations.TextAnnotation.class).substring(
                num.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class),
                num.get(CoreAnnotations.CharacterOffsetEndAnnotation.class)
        );
        assertEquals(expectedText, text);
        assertEquals(expectedType, num.get(CoreAnnotations.NumericCompositeTypeAnnotation.class));
        assertEquals(expectedNumber.toString(), num.get(CoreAnnotations.NumericCompositeValueAnnotation.class).toString());
      }
    }
    assertFalse(expectedNumbers.hasNext());
  }

  public void testOrdinals() throws IOException {
    // Set up test text
    String testText =
            "0th, 1st, 2nd, 3rd, 4th, 5th, 6th, 7th, 8th, 9th, 10th\n" +
            "zeroth, first, second, third, fourth, fifth, sixth, seventh, eighth, ninth, tenth\n" +
            "11th, 12th, 13th, 14th, 15th, 16th, 17th, 18th, 19th, 20th\n" +
            "Eleventh, twelfth, thirteenth, Fourteenth, fifteenth, Sixteenth, seventeenth, eighteenth, nineteenth, twentieth\n" +
            "Twenty-first, twenty first, twenty second, twenty third, twenty fourth\n" +
            "thirtieth, thirty first, thirty-second," +
      "fortieth, one hundredth, two hundredth, one hundred and fifty first, one hundred fifty first";

    // TODO: Fix consistency of number representation
    // set up expected results
    Iterator<? extends Number> expectedNumbers =
            Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
                          0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
                          11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
                          11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
                          21.0, 21.0, 22.0, 23.0, 24.0, 30, 31.0, 32.0, 40, 100.0, 200.0, 151.0, 151.0).iterator();
    Iterator<String> expectedTexts = Arrays.asList(testText.split("\\s*[,\\n]+\\s*")).iterator();

    // create document
    Annotation document = createDocument(testText);

    // Annotate numbers
    NumberNormalizer.findAndAnnotateNumericExpressions(document);

    // Check answers
    for (CoreMap num: document.get(CoreAnnotations.NumerizedTokensAnnotation.class)) {
      if (num.has(CoreAnnotations.NumericCompositeTypeAnnotation.class)) {
        Number expectedNumber = expectedNumbers.next();
        String expectedType = "ORDINAL";
        String expectedText = expectedTexts.next();
        String text = document.get(CoreAnnotations.TextAnnotation.class).substring(
                num.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class),
                num.get(CoreAnnotations.CharacterOffsetEndAnnotation.class)
        );
        assertEquals(expectedText, text);
        assertEquals("Type for " + expectedText, expectedType, num.get(CoreAnnotations.NumericCompositeTypeAnnotation.class));
        assertEquals(expectedNumber.toString(), num.get(CoreAnnotations.NumericCompositeValueAnnotation.class).toString());
      }
    }
    assertFalse(expectedNumbers.hasNext());
  }

  private static Annotation createDocument(String text) {
    Annotation annotation = new Annotation(text);
    pipeline.annotate(annotation);
    return annotation;
  }

}
