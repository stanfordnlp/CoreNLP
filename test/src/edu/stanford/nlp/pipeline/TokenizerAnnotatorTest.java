package edu.stanford.nlp.pipeline;

import java.util.*;

import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.util.PropertiesUtils;
import junit.framework.TestCase;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;


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
    props.setProperty("tokenize.options", "ptb3Escaping=true");
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
    System.err.println(toks2);
    assertEquals(27, toks2.size());
  }

  public void testNumericHyphens() {
    String test = "We won 13-2. It went public in mid-1983. He was prime minister 1983-1989 and/or an 11-year-old man. ";
    String oldWords = "We won 13-2 . It went public in mid-1983 . He was prime minister 1983-1989 and/or an 11-year-old man .";
    String newWords = "We won 13 - 2 . It went public in mid-1983 . " +
            "He was prime minister 1983 - 1989 and/or an 11 - year - old man .";
    String newWords3 = "We won 13 - 2 . It went public in mid-1983 . " +
            "He was prime minister 1983 - 1989 and / or an 11 - year - old man .";
    String newWords4 = "We won 13-2 . It went public in mid-1983 . He was prime minister 1983-1989 and / or an 11-year-old man .";


    Properties props = PropertiesUtils.asProperties("annotators", "tokenize", "tokenize.options", "ptb3Escaping=true");
    Annotation ann = new Annotation(test);
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    pipeline.annotate(ann);
    List<CoreLabel> toks = ann.get(CoreAnnotations.TokensAnnotation.class);
    String out = SentenceUtils.listToString(toks);
    assertEquals(oldWords, out);

    Properties props2 = PropertiesUtils.asProperties("annotators", "tokenize", "tokenize.options", "splitForwardSlash=false,splitHyphenated=true,invertible");
    Annotation ann2 = new Annotation(test);
    StanfordCoreNLP pipeline2 = new StanfordCoreNLP(props2);
    pipeline2.annotate(ann2);
    List<CoreLabel> toks2 = ann2.get(CoreAnnotations.TokensAnnotation.class);
    String out2 = SentenceUtils.listToString(toks2);
    System.err.println(toks2);
    assertEquals(newWords, out2);

    Properties props3 = PropertiesUtils.asProperties("annotators", "tokenize", "tokenize.options", "splitHyphenated,splitForwardSlash");
    Annotation ann3 = new Annotation(test);
    StanfordCoreNLP pipeline3 = new StanfordCoreNLP(props3);
    pipeline3.annotate(ann3);
    List<CoreLabel> toks3 = ann3.get(CoreAnnotations.TokensAnnotation.class);
    String out3 = SentenceUtils.listToString(toks3);
    System.err.println(toks3);
    assertEquals(newWords3, out3);

    Properties props4 = PropertiesUtils.asProperties("annotators", "tokenize", "tokenize.options", "ptb3Escaping=true,splitForwardSlash");
    Annotation ann4 = new Annotation(test);
    StanfordCoreNLP pipeline4 = new StanfordCoreNLP(props4);
    pipeline4.annotate(ann4);
    List<CoreLabel> toks4 = ann4.get(CoreAnnotations.TokensAnnotation.class);
    String out4 = SentenceUtils.listToString(toks4);
    assertEquals(newWords4, out4);

    Properties props5 = PropertiesUtils.asProperties("annotators", "tokenize", "tokenize.options", "ud");
    Annotation ann5 = new Annotation(test);
    StanfordCoreNLP pipeline5 = new StanfordCoreNLP(props5);
    pipeline5.annotate(ann5);
    List<CoreLabel> toks5 = ann5.get(CoreAnnotations.TokensAnnotation.class);
    String out5 = SentenceUtils.listToString(toks5);
    assertEquals(newWords3, out5);

  }

  /** Test a few key values which should be set on the tokens */
  public void testBeforeAfterOffsets() {
    String test = "   Unban mox  opal!";
    List<String> words = Arrays.asList("Unban", "mox", "opal", "!");
    List<String> before = Arrays.asList("   ", " ", "  ", "");
    List<String> after = Arrays.asList(" ", "  ", "", "");
    int[] beginOffsets = { 3, 9, 14, 18 };
    int[] endOffsets = { 8, 12, 18, 19 };

    Properties props = PropertiesUtils.asProperties("annotators", "tokenize", "tokenize.options", "ptb3Escaping=true");
    Annotation ann = new Annotation(test);
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    pipeline.annotate(ann);
    
    List<CoreLabel> toks = ann.get(CoreAnnotations.TokensAnnotation.class);
    assertEquals(4, toks.size());
    for (int i = 0; i < toks.size(); ++i) {
      CoreLabel tok = toks.get(i);
      assertEquals(words.get(i),    tok.get(CoreAnnotations.TextAnnotation.class));
      assertEquals(before.get(i),   tok.get(CoreAnnotations.BeforeAnnotation.class));
      assertEquals(after.get(i),    tok.get(CoreAnnotations.AfterAnnotation.class));
      assertEquals(beginOffsets[i], tok.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class).intValue());
      assertEquals(endOffsets[i],   tok.get(CoreAnnotations.CharacterOffsetEndAnnotation.class).intValue());
    }
  }

  /** The word "gonna" at the end of the text should be tokenized.  See TokenizerAnnotator for details on this issue */
  public void testFinalGonna() {
    String test = "gonna";
    List<String> words = Arrays.asList("gon", "na");

    Properties props = PropertiesUtils.asProperties("annotators", "tokenize", "tokenize.options", "ptb3Escaping=true");
    Annotation ann = new Annotation(test);
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    pipeline.annotate(ann);
    
    List<CoreLabel> toks = ann.get(CoreAnnotations.TokensAnnotation.class);
    assertEquals(2, toks.size()); 
    for (int i = 0; i < toks.size(); ++i) {
      CoreLabel tok = toks.get(i);
      assertEquals(words.get(i), tok.get(CoreAnnotations.TextAnnotation.class));
    }
  }

  /*
  // [cdm] Need to work this out. It would be good to test that things work well with NLs in whitespace, but haven't yet....
  public void testNLsWhitespacePipeline() {
    String t = "Text with \n\n a new \nline.";
    List<String> tWords = Arrays.asList(
            "Text",
            "with",
            "a",
            "new",
            "line",
            ".");

    Properties props = PropertiesUtils.asProperties(
            "annotators", "tokenize,ssplit",
            "tokenize.whitespace", "true",
            "ssplit.newlineIsSentenceBreak", "always");
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
*/

}
