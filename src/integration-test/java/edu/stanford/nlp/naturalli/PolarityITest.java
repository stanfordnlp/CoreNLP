package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.StringUtils;
import org.junit.*;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.junit.Assert.*;


/**
 * A test to make sure {@link edu.stanford.nlp.naturalli.NaturalLogicAnnotator} marks the right polarities for the tokens
 * in the sentence.
 *
 * @author Gabor Angeli
 */
public class PolarityITest {

  private static final StanfordCoreNLP pipeline = new StanfordCoreNLP(new Properties(){{
    setProperty("annotators", "tokenize,ssplit,pos,lemma,depparse,natlog");  // TODO(gabor) replace me with depparse (but parse is faster to load)
    setProperty("ssplit.isOneSentence", "true");
    setProperty("tokenize.class", "PTBTokenizer");
    setProperty("tokenize.language", "en");
    setProperty("enforceRequirements", "false");
    setProperty("natlog.neQuantifiers", "true");
  }});

  private Polarity[] annotate(String text) {
    Annotation ann = new Annotation(text);
    pipeline.annotate(ann);
    List<CoreLabel> tokens = ann.get(CoreAnnotations.SentencesAnnotation.class).get(0).get(CoreAnnotations.TokensAnnotation.class);
    Polarity[] polarities = new Polarity[tokens.size()];
    for (int i = 0; i < tokens.size(); ++i) {
      polarities[i] = tokens.get(i).get(NaturalLogicAnnotations.PolarityAnnotation.class);
    }
    return polarities;
  }


  /**
   * Check a single sentence
   */
  private void check(String[] expected, String sentence) {
    Polarity[] actual = annotate(sentence);
    assertEquals("Expected polarities have wrong length (" + expected.length + " but got " + actual.length + ") for sentence: '" + sentence + "'", expected.length, actual.length);
    String expectedStr = StringUtils.join(Arrays.stream(expected).map(x -> {
      switch (x.toLowerCase()) {
        case "up":
        case "u":
        case "^":
          return "up";
        case "down":
        case "d":
        case "v":
          return "down";
        case "flat":
        case "f":
        case "-":
          return "flat";
        default:
          throw new IllegalArgumentException("Unknown polarity: " + x);
      }
    }).collect(Collectors.toList()), " ");
    String actualStr = StringUtils.join(Arrays.stream(actual).map(Polarity::toString).collect(Collectors.toList()), " ");
    assertEquals(sentence, expectedStr, actualStr);
  }

  /**
   * Check a single sentence
   */
  private void check(String expected, String sentence) {
    check(expected.split(" "), sentence);
  }


  @Test
  public void allCatsHaveTails() {
    Polarity[] p = annotate("all cats have tails");
    assertTrue(p[0].isUpwards());
    assertTrue(p[1].isDownwards());
    assertTrue(p[2].isUpwards());
    assertTrue(p[3].isUpwards());
  }

  @Test
  public void thereIsNoDoubtThatCatsHaveTails() {
    check("^ ^ ^ v ^ ^ ^ ^ v", "There is no doubt that cats have tails.");
  }

  @Test
  public void someCatsDontHaveTails() {
    Polarity[] p = annotate("some cats don't have tails");
    assertTrue(p[0].isUpwards());
    assertTrue(p[1].isUpwards());
    assertTrue(p[2].isUpwards());
    assertTrue(p[3].isUpwards());
    assertTrue(p[4].isDownwards());
    assertTrue(p[5].isDownwards());
  }

  @Test
  public void complexProperNouns() {
    Polarity[] p = annotate("Kip , his brothers , and Fletcher also played the Denver area bar scene while calling themselves Colorado .");
    assertTrue(p[0].isUpwards());
    for (int i = 1; i < p.length; ++i) {
      assertTrue(p[i].isUpwards());
    }
  }


  @Test
  public void temporalTestCases() {
    // check("^ ^ v v", "Can not do Tuesday"); // Broken due to different parse
    check("v ^ ^ v v v", "Tuesday is not good for me");
    check("v ^ ^ v", "Tuesday won't work");

    check("^ ^ v v v v", "Can't make it on Tuesday");
    check("v ^ ^ v v", "I can't make tomorrow");
    check("^ ^ v v", "Anytime except next tuesday");
    check("^ ^ ^ v", "No, not Tuesday");
    // check("v v v ^ ^ v v", "No, I can't do Tuesday");
    // check("v v ^ ^ v v", "No I can't do Tuesday");


  }

}
