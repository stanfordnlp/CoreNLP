package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.TestPaths;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.Properties;

/**
 * Test cases for TokensRegexNERAnnotator (taken from RegexNERAnnotator).
 *
 * @author Angel Chang
 */
public class TokensRegexNERAnnotatorITest {

  private static final String REGEX_ANNOTATOR_NAME = "tokensregexner";
  private static final String MAPPING = String.format("%s/TAC-KBP2010/sentence_extraction/itest_map", TestPaths.testHome());

  private static StanfordCoreNLP pipeline;
  private static Annotator caseless;
  private static Annotator cased;
  private static Annotator annotator;

  @Before
  public void setUp() throws Exception {
    synchronized(TokensRegexNERAnnotatorITest.class) {
      if (pipeline == null) {  // Hack so we don't load the pipeline fresh for every test
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner");
        props.setProperty("ner.applyFineGrained", "false");
        props.setProperty("ner.buildEntityMentions", "false");
        pipeline = new StanfordCoreNLP(props);
        // Basic caseless and cased tokens regex annotators
        caseless = new TokensRegexNERAnnotator(MAPPING, true);
        cased = new TokensRegexNERAnnotator(MAPPING);
        annotator = cased;
      }
    }
  }

  // Helper methods
  private static TokensRegexNERAnnotator getTokensRegexNerAnnotator(Properties props) {
    return new TokensRegexNERAnnotator(REGEX_ANNOTATOR_NAME, props);
  }

  private static TokensRegexNERAnnotator getTokensRegexNerAnnotator(String[][] patterns, boolean ignoreCase) throws Exception {
    return getTokensRegexNerAnnotator(new Properties(), patterns, ignoreCase);
  }

  private static TokensRegexNERAnnotator getTokensRegexNerAnnotator(Properties props, String[][] patterns, boolean ignoreCase)
          throws Exception {
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
  private static void checkNerTags(List<CoreLabel> tokens, String... tags) {
    Assert.assertEquals(tags.length, tokens.size());
    for (int i = 0; i < tags.length; ++i) {
      Assert.assertEquals("Mismatch for token tag NER " + i + ' ' + tokens.get(i),
              tags[i], tokens.get(i).get(CoreAnnotations.NamedEntityTagAnnotation.class));
    }
  }

  private static void checkTags(List<CoreLabel> tokens, Class key, String... tags) {
    Assert.assertEquals(tags.length, tokens.size());
    for (int i = 0; i < tags.length; ++i) {
      Assert.assertEquals("Mismatch for token tag " + key + ' ' + i + ' ' + tokens.get(i),
              tags[i], tokens.get(i).get(key));
    }
  }

  /**
   * Helper method, re-annotate each token with specified tag
   */
  private static void reannotate(List<CoreLabel> tokens, Class key, String ... tags) {
    Assert.assertEquals(tags.length, tokens.size());
    for (int i = 0; i < tags.length; ++i) {
      tokens.get(i).set(key, tags[i]);
    }
  }

  // Tests for TokensRegex syntax
  @Test
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

    checkNerTags(tokens,
      "ORGANIZATION", "ORGANIZATION", "ORGANIZATION", "O", "O", "O", "LOCATION", "O");

    reannotate(tokens, CoreAnnotations.NamedEntityTagAnnotation.class,
            "O", "O", "LOCATION", "O", "O", "O", "LOCATION", "O");
    annotatorCased.annotate(document);

    checkNerTags(tokens,
      "SCHOOL", "SCHOOL", "SCHOOL", "O", "O", "O", "LOCATION", "O");

    // Try lowercase
    Annotator annotatorCaseless = getTokensRegexNerAnnotator(regexes, true);

    str = "university of alaska is located in alaska.";
    document = createDocument(str);
    tokens = document.get(CoreAnnotations.TokensAnnotation.class);
    checkNerTags(tokens,
      "O", "O", "LOCATION", "O", "O", "O", "LOCATION", "O");
    annotatorCased.annotate(document);
    checkNerTags(tokens,
      "O", "O", "LOCATION", "O", "O", "O", "LOCATION", "O");
    annotatorCaseless.annotate(document);
    checkNerTags(tokens,
      "SCHOOL", "SCHOOL", "SCHOOL", "O", "O", "O", "LOCATION", "O");
  }

  // Tests for TokensRegex syntax with match group
  @Test
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

    checkNerTags(tokens,
      "O", "O", "MOVIE", "O", "O", "O");

  }

  // Tests for TokensRegexNer annotator annotating other fields
  @Test
  public void testTokensRegexNormalizedAnnotate() throws Exception {
    Properties props = new Properties();
    props.setProperty(REGEX_ANNOTATOR_NAME + ".mapping.header", "pattern,ner,normalized,overwrite,priority,group");

    String[][] regexes =
      new String[][]{
        new String[]{"blue",  "COLOR", "B", "", "0"},
        new String[]{"red",   "COLOR", "R", "", "0"},
        new String[]{"green", "COLOR", "G", "", "0"}
      };
    Annotator annotatorCased = getTokensRegexNerAnnotator(props, regexes, false);

    String str = "These are all colors: blue, red, and green.";
    Annotation document = createDocument(str);
    annotatorCased.annotate(document);
    List<CoreLabel> tokens = document.get(CoreAnnotations.TokensAnnotation.class);

    checkTags(tokens, CoreAnnotations.TextAnnotation.class, "These", "are", "all", "colors", ":", "blue", ",", "red", ",", "and", "green", ".");
    checkTags(tokens, CoreAnnotations.NamedEntityTagAnnotation.class,  "O", "O", "O", "O", "O", "COLOR", "O", "COLOR", "O", "O", "COLOR", "O");
    checkTags(tokens, CoreAnnotations.NormalizedNamedEntityTagAnnotation.class,  null, null, null, null, null, "B", null, "R", null, null, "G", null);
  }

  public static class TestAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  // Tests for TokensRegexNer annotator annotating other fields with custom key mapping
  @Test
  public void testTokensRegexCustomAnnotate() throws Exception {

    Properties props = new Properties();
    props.setProperty(REGEX_ANNOTATOR_NAME + ".mapping.header", "pattern,test,overwrite,priority,group");
    props.setProperty(REGEX_ANNOTATOR_NAME + ".mapping.field.test", "edu.stanford.nlp.pipeline.TokensRegexNERAnnotatorITest$TestAnnotation");
    String[][] regexes =
      new String[][]{
        new String[]{"test", "TEST", "", "0"}
      };
    Annotator annotatorCased = getTokensRegexNerAnnotator(props, regexes, true);

    String str = "Marking all test as test";
    Annotation document = createDocument(str);
    annotatorCased.annotate(document);
    List<CoreLabel> tokens = document.get(CoreAnnotations.TokensAnnotation.class);

    checkTags(tokens, CoreAnnotations.TextAnnotation.class, "Marking", "all", "test", "as", "test");
    checkTags(tokens, TestAnnotation.class, null, null, "TEST", null, "TEST");
  }

  // Basic tests from RegexNERAnnotatorITest
  @Test
  public void testBasicMatching() {
    String str = "President Barack Obama lives in Chicago , Illinois , " +
    "and is a practicing Christian .";
    Annotation document = createDocument(str);
    annotator.annotate(document);
    List<CoreLabel> tokens = document.get(CoreAnnotations.TokensAnnotation.class);

    checkNerTags(tokens,
      "TITLE", "PERSON", "PERSON", "O", "O", "LOCATION", "O", "STATE_OR_PROVINCE",
      "O", "O", "O", "O", "O", "IDEOLOGY", "O");

  }

  /**
   * The ORGANIZATION on Ontario Bank should not ve overrwritten since Ontario (STATE_OR_PROVINCE)
   * does not span Ontario Bank. Nevertheless, by the special Chinese KBP 2016 hack, the LOCATION on Ontario Lake
   * should be overwritten.  Native American Church will overwrite ORGANIZATION with
   * RELIGION.
   */
  @Test
  public void testOverwrite() {
    String str = "I like Ontario Bank and Ontario Lake , and I like the Native American Church , too .";
    Annotation document = createDocument(str);
    annotator.annotate(document);
    List<CoreLabel> tokens = document.get(CoreAnnotations.TokensAnnotation.class);

    checkNerTags(tokens, "O", "O", "ORGANIZATION", "ORGANIZATION", "O", "STATE_OR_PROVINCE", "LOCATION", "O", "O", "O", "O", "O", "RELIGION",
      "RELIGION", "RELIGION", "O", "O", "O");

  }

  /**
   * In the mapping file, Christianity is assigned a higher priority than Early Christianity,
   * and so Early should not be marked as RELIGION.
   */
  @Test
  public void testPriority() {
    String str = "Christianity is of higher regex priority than Early Christianity . ";
    Annotation document = createDocument(str);
    annotator.annotate(document);
    List<CoreLabel> tokens = document.get(CoreAnnotations.TokensAnnotation.class);
    checkNerTags(tokens, "RELIGION", "O", "O", "O", "O", "O", "O", "O", "RELIGION", "O");
  }


  /**
   * Test that if there are no annotations at all, the annotator
   * throws an exception.  We are happy if we can catch an exception
   * and continue, and if we don't get any exceptions, we throw an
   * exception of our own.
   */
  @Test
  public void testEmptyAnnotation() {
    try {
      annotator.annotate(new Annotation(""));
    } catch(RuntimeException e) {
      return;
    }
    Assert.fail("Never expected to get this far... the annotator should have thrown an exception by now");
  }

}
