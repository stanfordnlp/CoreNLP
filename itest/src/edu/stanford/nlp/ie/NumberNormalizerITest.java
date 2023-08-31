package edu.stanford.nlp.ie;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.util.CoreMap;

import org.junit.*;

import java.util.Arrays;
import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Test cases for number normalizer.
 *
 * @author Angel Chang
 */
public class NumberNormalizerITest {

  private static AnnotationPipeline pipeline; // = null;
  private static final boolean VERBOSE = false;

  @BeforeClass
  public static void runOnceBeforeClass() {
    if (VERBOSE) {
      System.err.println("Setting up pipeline in @BeforeClasss");
    }
    pipeline = new AnnotationPipeline();
    pipeline.addAnnotator(new TokenizerAnnotator(false, "en", "invertible,splitHyphenated=false"));
    pipeline.addAnnotator(new POSTaggerAnnotator(DefaultPaths.DEFAULT_POS_MODEL, false));
  }

  @AfterClass
  public static void runOnceAfterClass() {
    System.err.println("Nulling pipeline in @AfterClass");
    pipeline = null;
  }

  @Test
  public void testNumbers() {
    // Set up test text
    String testText =
            "two dozen\n" +
            "\u2009405\n" +
            "six hundred,\n" +
            "four hundred, and twelve.\n" +
            "4 million six hundred fifty thousand, two hundred and eleven.\n" +
            "6 hundred billion, five million six hundred fifty thousand, three hundred and seventy six\n" +
            "5,786,345\n" +
            "twenty-five.\n" +

            // "one and a half million\n" +
            "1.3 million.\n" +
            "one thousand two hundred and twenty four\n" +
            "10 thousand million.\n"+
            "3.625\n" +

            "zero\n" +
            "-15\n" +
            "one two three four.\n" +
            "one hundred and fifty five\n" +
            "a hundred and one\n" +
//            "five oh four\n"
            "four score.\n" +
            "a dozen bagels\n" +
            "five dozen\n" +
            "An IQ score of 161.\n" +     // only 161, not 20 for score
            "thirty two\n" +
            "I hope Jennifer would let me lick her antennae even though I am forty-five\n" +
            "I hope Jennifer would let me lick her antennae even though I am fourty-five"
            ;

    // set up expected results
    Iterator<? extends Number> expectedNumbers = Arrays.asList(
            24.0, 405, 600.0, 412.0, 4650211.0, 600005650376.0, 5786345, 25.0,
            /* 1500000.0, */
            1300000.0, 1224.0, 10000000000.0, 3.625,
            0, -15.0, 1, 2, 3, 4, 155.0, 101.0 /*504.0, */, 80.0, 12, 60.0, 161, 32.0, 45.0, 45.0 ).iterator();
    Iterator<String> expectedTexts = Arrays.asList(
            "two dozen", "405", "six hundred", "four hundred, and twelve",
            "4 million six hundred fifty thousand, two hundred and eleven",
            "6 hundred billion, five million six hundred fifty thousand, three hundred and seventy six",
            "5,786,345",
            "twenty-five",
            // "one and half million",
            "1.3 million",
            "one thousand two hundred and twenty four",
            "10 thousand million",
            "3.625",
            "zero", "-15", "one", "two", "three", "four",
            "one hundred and fifty five",
            "hundred and one" /* "five oh four", */,
            "four score",
            "dozen",
            "five dozen",
            "161", "thirty two", "forty-five", "fourty-five").iterator();

    // create document
    Annotation document = createDocument(testText);

    // Annotate numbers
    NumberNormalizer.findAndAnnotateNumericExpressions(document);

    // Check answers
    for (CoreMap num: document.get(CoreAnnotations.NumerizedTokensAnnotation.class)) {
      if (num.containsKey(CoreAnnotations.NumericCompositeTypeAnnotation.class)) {
        Number expectedNumber = expectedNumbers.next();
        String expectedType = "NUMBER";
        String expectedText = expectedTexts.next();
        String text = document.get(CoreAnnotations.TextAnnotation.class).substring(
                num.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class),
                num.get(CoreAnnotations.CharacterOffsetEndAnnotation.class)
        );
        if (VERBOSE) {
          System.err.printf("Found %s of type %s with value %s%n",
                  text,
                  num.get(CoreAnnotations.NumericCompositeTypeAnnotation.class),
                  num.get(CoreAnnotations.NumericCompositeValueAnnotation.class));
        }
        assertEquals(expectedText, text);
        assertEquals(expectedType, num.get(CoreAnnotations.NumericCompositeTypeAnnotation.class));
        assertEquals(expectedNumber.toString(), num.get(CoreAnnotations.NumericCompositeValueAnnotation.class).toString());
        // This doesn't work as sometimes type is different
        // assertEquals(expectedNumber, num.get(CoreAnnotations.NumericCompositeValueAnnotation.class));
      // } else if (VERBOSE) {
        // System.err.println("num is " + num.toShorterString());
      }
    }
    assertFalse("expectedNumbers were left which didn't show up in the document", expectedNumbers.hasNext());
    assertFalse("expectedTexts were left which didn't show up in the document", expectedTexts.hasNext());
  }

  @Test
  public void testOrdinals() {
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
      if (num.containsKey(CoreAnnotations.NumericCompositeTypeAnnotation.class)) {
        Number expectedNumber = expectedNumbers.next();
        String expectedType = "ORDINAL";
        String expectedText = expectedTexts.next();
        String text = document.get(CoreAnnotations.TextAnnotation.class).substring(
                num.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class),
                num.get(CoreAnnotations.CharacterOffsetEndAnnotation.class)
        );
        if (VERBOSE) {
          System.err.printf("Found %s of type %s with value %s%n",
                  text,
                  num.get(CoreAnnotations.NumericCompositeTypeAnnotation.class),
                  num.get(CoreAnnotations.NumericCompositeValueAnnotation.class));
        }
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
