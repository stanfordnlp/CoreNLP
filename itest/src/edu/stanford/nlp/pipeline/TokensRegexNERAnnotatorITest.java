package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.StringUtils;
import junit.framework.TestCase;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.Properties;

/**
 * Test cases for TokensRegexNERAnnotator (taken from RegexNERAnnotator)
 * @author Angel Chang
 */
public class TokensRegexNERAnnotatorITest extends TestCase {
  private static final String REGEX_ANNOTATOR_NAME = "tokensregexner";
  private static final String MAPPING = "/u/nlp/data/TAC-KBP2010/sentence_extraction/itest_map";

  private static StanfordCoreNLP pipeline;
  private static Annotator caseless;
  private static Annotator cased;
  private static Annotator annotator;

  @Override
  public void setUp() throws Exception {
    synchronized(TokensRegexNERAnnotatorITest.class) {
      if (pipeline == null) {  // Hack so we don't load the pipeline fresh for every test
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner");
        pipeline = new StanfordCoreNLP(props);
        // Basic caseless and cased tokens regex annotators
        caseless = new TokensRegexNERAnnotator(MAPPING, true);
        cased = new TokensRegexNERAnnotator(MAPPING);
        annotator = cased;
      }
    }
  }

  // Helper methods
  protected static TokensRegexNERAnnotator getTokensRegexNerAnnotator(Properties props)
  {
    return new TokensRegexNERAnnotator(REGEX_ANNOTATOR_NAME, props);
  }

  protected static TokensRegexNERAnnotator getTokensRegexNerAnnotator(String[][] patterns, boolean ignoreCase) throws Exception
  {
    Properties props = new Properties();
    File tempFile = File.createTempFile("tokensregexnertest.patterns", "txt");
    tempFile.deleteOnExit();
    PrintWriter pw = IOUtils.getPrintWriter(tempFile.getAbsolutePath());
    for (String[] p: patterns) {
      pw.println(StringUtils.join(p, "\t"));
    }
    pw.close();
    props.setProperty(REGEX_ANNOTATOR_NAME + ".mapping", tempFile.getAbsolutePath());
    props.setProperty(REGEX_ANNOTATOR_NAME + ".ignorecase", String.valueOf(ignoreCase));
    return new TokensRegexNERAnnotator(REGEX_ANNOTATOR_NAME, props);
  }

  protected static Annotation createDocument(String text) {
    Annotation annotation = new Annotation(text);
    pipeline.annotate(annotation);
    return annotation;
  }

  /**
   * Helper method, checks that each token is tagged with the expected NER type.
   */
  private static void checkTags(List<CoreLabel> tokens, String ... tags) {
    assertEquals(tags.length, tokens.size());
    for (int i = 0; i < tags.length; ++i) {
      assertEquals("Mismatch for token " + i + " " + tokens.get(i),
                   tags[i], tokens.get(i).get(CoreAnnotations.NamedEntityTagAnnotation.class));
    }
  }

  /**
   * Helper method, re-annotate each token with specified tag
   */
  private static void reannotate(List<CoreLabel> tokens, Class key, String ... tags) {
    assertEquals(tags.length, tokens.size());
    for (int i = 0; i < tags.length; ++i) {
      tokens.get(i).set(key, tags[i]);
    }
  }

  // Tests for TokensRegex syntax
  public void testTokensRegexSyntax() throws Exception {
    String[][] regexes =
      new String[][]{
        new String[]{"( /University/ /of/ [ {ner:LOCATION} ] )", "SCHOOL"}
        // TODO: TokensRegex literal string patterns ignores ignoreCase settings
        //new String[]{"( University of [ {ner:LOCATION} ] )", "SCHOOL"}
    };
    Annotator annotatorCased = getTokensRegexNerAnnotator(regexes, false);

    String str = "University of Alaska is located in Alaska.";
    Annotation document = createDocument(str);
    annotatorCased.annotate(document);
    List<CoreLabel> tokens = document.get(CoreAnnotations.TokensAnnotation.class);

    checkTags(tokens,
            "ORGANIZATION", "ORGANIZATION", "ORGANIZATION", "O", "O", "O", "LOCATION", "O");

    reannotate(tokens, CoreAnnotations.NamedEntityTagAnnotation.class,
            "O", "O", "LOCATION", "O", "O", "O", "LOCATION", "O");
    annotatorCased.annotate(document);

    checkTags(tokens,
            "SCHOOL", "SCHOOL", "SCHOOL", "O", "O", "O", "LOCATION", "O");

    // Try lowercase
    Annotator annotatorCaseless = getTokensRegexNerAnnotator(regexes, true);

    str = "university of alaska is located in alaska.";
    document = createDocument(str);
    tokens = document.get(CoreAnnotations.TokensAnnotation.class);
    checkTags(tokens,
              "O", "O", "LOCATION", "O", "O", "O", "LOCATION", "O");
    annotatorCased.annotate(document);
    checkTags(tokens,
              "O", "O", "LOCATION", "O", "O", "O", "LOCATION", "O");
    annotatorCaseless.annotate(document);
    checkTags(tokens,
              "SCHOOL", "SCHOOL", "SCHOOL", "O", "O", "O", "LOCATION", "O");
  }

  // Tests for TokensRegex syntax with match group
  public void testTokensRegexMatchGroup() throws Exception {
    String[][] regexes =
      new String[][]{
        new String[]{"( /the/? /movie/ (/[A-Z].*/+) )", "MOVIE", "", "0", "1"}
      };
    Annotator annotatorCased = getTokensRegexNerAnnotator(regexes, false);

    String str = "the movie Mud was very muddy";
    Annotation document = createDocument(str);
    annotatorCased.annotate(document);
    List<CoreLabel> tokens = document.get(CoreAnnotations.TokensAnnotation.class);

    checkTags(tokens,
      "O", "O", "MOVIE", "O", "O", "O");

  }

  // Basic tests from RegexNERAnnotatorITest
  public void testBasicMatching() throws Exception {
    String str = "President Barack Obama lives in Chicago , Illinois , " +
    "and is a practicing Christian .";
    Annotation document = createDocument(str);
    annotator.annotate(document);
    List<CoreLabel> tokens = document.get(CoreAnnotations.TokensAnnotation.class);

    checkTags(tokens,
            "TITLE", "PERSON", "PERSON", "O", "O", "LOCATION", "O", "STATE_OR_PROVINCE",
            "O", "O", "O", "O", "O", "IDEOLOGY", "O");

  }

  /**
   * The LOCATION on Ontario Place should not be overwritten since Ontario (STATE_OR_PROVINCE)
   * does not span Ontario Place.  Native American Church will overwrite ORGANIZATION with
   * RELIGION.
   */
  public void testOverwrite() throws Exception {
    String str = "I like Ontario Place , and I like the Native American Church , too .";
    Annotation document = createDocument(str);
    annotator.annotate(document);
    List<CoreLabel> tokens = document.get(CoreAnnotations.TokensAnnotation.class);

    checkTags(tokens, "O", "O", "LOCATION", "LOCATION", "O", "O", "O", "O", "O", "RELIGION",
        "RELIGION", "RELIGION", "O", "O", "O");

  }

  /**
   * In the mapping file, Christianity is assigned a higher priority than Early Christianity,
   * and so Early should not be marked as RELIGION.
   */
  public void testPriority() throws Exception {
    String str = "Christianity is of higher regex priority than Early Christianity . ";
    Annotation document = createDocument(str);
    annotator.annotate(document);
    List<CoreLabel> tokens = document.get(CoreAnnotations.TokensAnnotation.class);
    checkTags(tokens, "RELIGION", "O", "O", "O", "O", "O", "O", "O", "RELIGION", "O");
  }


  /**
   * Test that if there are no annotations at all, the annotator
   * throws an exception.  We are happy if we can catch an exception
   * and continue, and if we don't get any exceptions, we throw an
   * exception of our own.
   */
  public void testEmptyAnnotation() throws Exception {
    try {
      annotator.annotate(new Annotation(""));
    } catch(RuntimeException e) {
      return;
    }
    fail("Never expected to get this far... the annotator should have thrown an exception by now");
  }

}
