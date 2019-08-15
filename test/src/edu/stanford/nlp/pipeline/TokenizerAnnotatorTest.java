package edu.stanford.nlp.pipeline;

import java.util.*;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import junit.framework.TestCase;


/**
 * See TokenizerAnnotatorITest for some tests that require model files.
 * See PTBTokenizerTest, etc. for more detailed language-specific tests.
 *
 * @author Christopher Manning
 */
public class TokenizerAnnotatorTest extends TestCase {

  private static final String text = "She'll prove it ain't so.";
  private static List<String> tokenWords = Arrays.asList(
          "She",
          "'ll",
          "prove",
          "it",
          "ai",
          "n't",
          "so",
          ".");

  public void testNewVersion() {
    Annotation ann = new Annotation(text);
    Annotator annotator = new TokenizerAnnotator("en");
    annotator.annotate(ann);
    Iterator<String> it = tokenWords.iterator();
    for (CoreLabel word : ann.get(CoreAnnotations.TokensAnnotation.class)) {
      assertEquals("Bung token in new CoreLabel usage", it.next(), word.word());
    }
    assertFalse("Too few tokens in new CoreLabel usage", it.hasNext());

    Iterator<String> it2 = tokenWords.iterator();
    for (CoreLabel word : ann.get(CoreAnnotations.TokensAnnotation.class)) {
      assertEquals("Bung token in new CoreLabel usage", it2.next(), word.get(CoreAnnotations.TextAnnotation.class));
    }
    assertFalse("Too few tokens in new CoreLabel usage", it2.hasNext());
  }

  public void testBadLanguage() {
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize");
    props.setProperty("tokenize.language", "notalanguage");
    try {
      new StanfordCoreNLP(props);
      throw new RuntimeException("Should have failed");
    } catch (IllegalArgumentException e) {
      // yay, passed
    }
  }

  public void testDefaultNoNLsPipeline() {
    String t = "Text with \n\n a new \nline.";
    List<String> tWords = Arrays.asList(
            "Text",
            "with",
            "a",
            "new",
            "line",
            ".");

    Properties props = new Properties();
    props.setProperty("annotators", "tokenize");
    Annotation ann = new Annotation(t);
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    pipeline.annotate(ann);
    Iterator<String> it = tWords.iterator();
    for (CoreLabel word : ann.get(CoreAnnotations.TokensAnnotation.class)) {
      assertEquals("Bung token in new CoreLabel usage", it.next(), word.word());
    }
    assertFalse("Too few tokens in new CoreLabel usage", it.hasNext());

    Iterator<String> it2 = tWords.iterator();
    for (CoreLabel word : ann.get(CoreAnnotations.TokensAnnotation.class)) {
      assertEquals("Bung token in new CoreLabel usage", it2.next(), word.get(CoreAnnotations.TextAnnotation.class));
    }
    assertFalse("Too few tokens in new CoreLabel usage", it2.hasNext());
  }

  public void testHyphens() {
    String test = "Hyphen-ated words should be split except when school-aged-children eat " +
        "anti-disestablishmentariansm for breakfast at the o-kay choral infront of some explor-o-toriums.";
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize");
    Annotation ann = new Annotation(test);
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    pipeline.annotate(ann);
    List<CoreLabel> toks = ann.get(CoreAnnotations.TokensAnnotation.class);
    assertEquals(21, toks.size());

    Properties props2 = new Properties();
    props2.setProperty("annotators", "tokenize");
    props2.setProperty("tokenize.options", "splitHyphenated=true");
    Annotation ann2 = new Annotation(test);
    StanfordCoreNLP pipeline2 = new StanfordCoreNLP(props2);
    pipeline2.annotate(ann2);
    List<CoreLabel> toks2 = ann2.get(CoreAnnotations.TokensAnnotation.class);
    assertEquals(27, toks2.size());
  }

}
