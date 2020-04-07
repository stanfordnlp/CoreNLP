package edu.stanford.nlp.ie.qe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import edu.stanford.nlp.ling.tokensregex.MatchedExpression;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.AnnotationPipeline;
import edu.stanford.nlp.pipeline.DefaultPaths;
import edu.stanford.nlp.pipeline.POSTaggerAnnotator;
import edu.stanford.nlp.pipeline.TokenizerAnnotator;
import edu.stanford.nlp.pipeline.WordsToSentencesAnnotator;

/**
 * Test for quantifiable entity extractor.
 *
 * @author Angel Chang
 */
public class QuantifiableEntityExtractorITest {

  private static AnnotationPipeline pipeline; // = null;
  private static QuantifiableEntityExtractor extractor; // = null;

  @Test
  public void test() throws Exception {
    // TODO: Enable tests after rules files are added to models
  }

  @Before
  public void setUp() throws Exception {
    synchronized(QuantifiableEntityExtractorITest.class) {
      if (pipeline == null) {
        pipeline = new AnnotationPipeline();
        pipeline.addAnnotator(new TokenizerAnnotator(false, "en"));
        pipeline.addAnnotator(new WordsToSentencesAnnotator(false));
        pipeline.addAnnotator(new POSTaggerAnnotator(DefaultPaths.DEFAULT_POS_MODEL, false));
        //pipeline.addAnnotator(new QuantifiableEntityNormalizingAnnotator(false, false));
      }
      extractor = new QuantifiableEntityExtractor();
      //extractor.init(new Options());
    }
  }

  protected static Annotation createDocument(String text) {
    Annotation annotation = new Annotation(text);
    pipeline.annotate(annotation);
    return annotation;
  }

  public static class ExpectedQuantity {
    String text;
    String normalizedValue;
    String type;

    public ExpectedQuantity(String text, String normalizedValue, String type) {
      this.text = text;
      this.normalizedValue = normalizedValue;
      this.type = type;
    }
  }

  private static void runAndCheck(String prefix, String[] sentences, ExpectedQuantity[][] expected) {
    for (int si = 0; si < sentences.length; si++) {
      String sentence = sentences[si];
      Annotation annotation = createDocument(sentence);
      List<MatchedExpression> matchedExpressions = extractor.extract(annotation);

      // Print out matched text and value
      if (expected == null) {
        for (MatchedExpression matchedExpression : matchedExpressions) {
          String text = matchedExpression.getText();
          Object value = matchedExpression.getValue();
          System.out.println(prefix + ": Got expression " + text + " with value " + value);
        }
        fail(prefix + ": No expected provided");
      } else {
        int minMatchable = Math.min(expected[si].length, matchedExpressions.size());
        for (int i = 0; i < minMatchable; i++) {
          ExpectedQuantity expectedQuantity = expected[si][i];
          MatchedExpression matched = matchedExpressions.get(i);
          SimpleQuantifiableEntity actualQuantity = (SimpleQuantifiableEntity) matched.getValue().get();
          assertEquals(prefix + ".matched." + si + "." + i + ".text", expectedQuantity.text, matched.getText());
          assertEquals(prefix + ".matched." + si + "." + i + ".normalizedValue", expectedQuantity.normalizedValue, actualQuantity.toString());
          assertEquals(prefix + ".matched." + si + "." + i + ".type", expectedQuantity.type, actualQuantity.getUnit().type);
        }
        assertEquals(prefix + ".length." + si, expected[si].length, matchedExpressions.size());
      }
    }
  }

  public static void _testMoney() throws Exception {
    String[] sentences = {
        "I have 1 dollar and 2 cents.",
        "It cost 10 thousand million dollars."
    };
    // TODO: merge the 1 dollar and 2 cents
    ExpectedQuantity[][] expected = {
        {new ExpectedQuantity("1 dollar", "$1.00", "MONEY"), new ExpectedQuantity("2 cents", "$0.02", "MONEY")},
        {new ExpectedQuantity("10 thousand million dollars", "$10000000000.00", "MONEY")}
    };

    runAndCheck("testMoney", sentences, expected);
  }

  public static void _testLength() {
    String[] sentences = {
        "We are 2 kilometer away.",
        "We are 2 kilometers away.",
        "We turn after 5 miles.",
        "The box is 100 centimeters tall.",
        "The box is 10cm wide.",
        "The box is over 1000 mm long.",
        "The box is 2ft long."
    };
    ExpectedQuantity[][] expected = {
        {new ExpectedQuantity("2 kilometer", "2000.0m", "LENGTH")},
        {new ExpectedQuantity("2 kilometers", "2000.0m", "LENGTH")},
        {new ExpectedQuantity("5 miles", "5.0mi", "LENGTH")},
        {new ExpectedQuantity("100 centimeters", "1.0m", "LENGTH")},
        {new ExpectedQuantity("10cm", "0.1m", "LENGTH")},
        {new ExpectedQuantity("1000 mm", "1.0m", "LENGTH")},
        {new ExpectedQuantity("2ft", "2.0'", "LENGTH")}
    };
    runAndCheck("testLength", sentences, expected);
  }

  // We do weight instead of mass since in typical natural language
  //  kilograms are used to refer to weight vs mass (in scientific usage)
  public static void _testWeight() {
    String[] sentences = {
        "The ball is 2 kilograms in weight.",
        "There are five grams.",
        "How much is seven pounds?"
    };
    ExpectedQuantity[][] expected = {
        {new ExpectedQuantity("2 kilograms", "2.0kg", "WEIGHT")},
        {new ExpectedQuantity("five grams", "0.005kg", "WEIGHT")},
        {new ExpectedQuantity("seven pounds", "7.0lb", "WEIGHT")}
    };
    runAndCheck("testWeight", sentences, expected);
  }

}
