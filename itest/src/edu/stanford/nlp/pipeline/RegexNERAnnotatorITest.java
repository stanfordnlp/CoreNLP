package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.SentenceUtils;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;

/** @author jtibs */
public class RegexNERAnnotatorITest extends TestCase {
  private static final String MAPPING = "/Users/tony/tmp/itest_map";
  private static RegexNERAnnotator annotator;

  @Override
  public void setUp() throws Exception {
    synchronized(RegexNERAnnotator.class) {
      if (annotator == null) {
        annotator = new RegexNERAnnotator(MAPPING, false, null);
      }
    }
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

  public void testBasicMatching() {
    String str = "President Barack Obama lives in Chicago , Illinois , " +
    "and is a practicing Christian .";
    String text = "Gillard was born in Barry, Wales, and migrated with her family to Adelaide, South Australia, in 1966, attending Mitcham Demonstration School and Unley High School. In 1982, she moved to Melbourne, Victoria. She graduated from the University of Melbourne with a Bachelor of Arts and a Bachelor of Laws in 1986. In 1987, Gillard joined the law firm Slater & Gordon, specialising in industrial law, before entering politics. Annotations are the data structure which hold the results of annotators. Annotations are basically maps, from keys to bits of the annotation, such as the parse, the part-of-speech tags, or named entity tags. Annotators are a lot like functions, except that they operate over Annotations instead of Objects. They do things like tokenize, parse, or NER tag sentences. Annotators and Annotations are integrated by AnnotationPipelines, which create sequences of generic Annotators. Stanford CoreNLP inherits from the AnnotationPipeline class, and is customized with NLP Annotators.";

    String[] split = str.split(" ");

    List<CoreLabel> tokens = SentenceUtils.toCoreLabelList(split);
    tokens.get(1).set(CoreAnnotations.NamedEntityTagAnnotation.class, "PERSON");
    tokens.get(2).set(CoreAnnotations.NamedEntityTagAnnotation.class, "PERSON");
    tokens.get(5).set(CoreAnnotations.NamedEntityTagAnnotation.class, "LOCATION");
    tokens.get(7).set(CoreAnnotations.NamedEntityTagAnnotation.class, "LOCATION");

    CoreMap sentence = new ArrayCoreMap();
    sentence.set(CoreAnnotations.TokensAnnotation.class, tokens);

    List<CoreMap> sentences = new ArrayList<CoreMap>();
    sentences.add(sentence);

    Annotation corpus = new Annotation("President Barack Obama lives in Chicago, Illinois," +
        "and is a practicing Christian.");
    corpus.set(CoreAnnotations.SentencesAnnotation.class, sentences);

    annotator.annotate(corpus);

    checkTags(tokens, "TITLE", "PERSON", "PERSON", "O", "O", "LOCATION", "O", "STATE_OR_PROVINCE",
        "O", "O", "O", "O", "O", "IDEOLOGY", "O");
  }

  /**
   * Neither the LOCATION nor the ORGANIZATION tags should be overridden, since both
   * Ontario (STATE_OR_PROVINCE) and American (NATIONALITY) do not span the entire
   * phrase that is NamedEntityTag-annotated.
   */
  public void testOverwrite() {
    String str = "I like Ontario Place , and I like the Native American Church , too .";
    String[] split = str.split(" ");

    List<CoreLabel> tokens = SentenceUtils.toCoreLabelList(split);
    tokens.get(2).set(CoreAnnotations.NamedEntityTagAnnotation.class, "LOCATION");
    tokens.get(3).set(CoreAnnotations.NamedEntityTagAnnotation.class, "LOCATION");
    tokens.get(9).set(CoreAnnotations.NamedEntityTagAnnotation.class, "ORGANIZATION");
    tokens.get(10).set(CoreAnnotations.NamedEntityTagAnnotation.class, "ORGANIZATION");
    tokens.get(11).set(CoreAnnotations.NamedEntityTagAnnotation.class, "ORGANIZATION");

    CoreMap sentence = new ArrayCoreMap();
    sentence.set(CoreAnnotations.TokensAnnotation.class, tokens);

    List<CoreMap> sentences = new ArrayList<CoreMap>();
    sentences.add(sentence);

    Annotation corpus = new Annotation("I like Ontario Place, and I like the Native" +
        "American Church, too.");
    corpus.set(CoreAnnotations.SentencesAnnotation.class, sentences);

    annotator.annotate(corpus);

    checkTags(tokens, "O", "O", "LOCATION", "LOCATION", "O", "O", "O", "O", "O", "RELIGION",
        "RELIGION", "RELIGION", "O", "O", "O");
  }

  /**
   * In the mapping file, Christianity is assigned a higher priority than Early Christianity,
   * and so Early should not be marked as RELIGION.
   */
  public void testPriority() {
    String str = "Christianity is of higher regex priority than Early Christianity . ";
    String[] split = str.split(" ");

    List<CoreLabel> tokens = SentenceUtils.toCoreLabelList(split);

    CoreMap sentence = new ArrayCoreMap();
    sentence.set(CoreAnnotations.TokensAnnotation.class, tokens);

    List<CoreMap> sentences = new ArrayList<CoreMap>();
    sentences.add(sentence);

    Annotation corpus = new Annotation("Christianity is of higher regex priority than Early " +
        "Christianity. ");
    corpus.set(CoreAnnotations.SentencesAnnotation.class, sentences);

    annotator.annotate(corpus);

    checkTags(tokens, "RELIGION", "O", "O", "O", "O", "O", "O", "O", "RELIGION", "O");
  }


  /**
   * Test that if there are no annotations at all, the annotator
   * throws an exception.  We are happy if we can catch an exception
   * and continue, and if we don't get any exceptions, we throw an
   * exception of our own.
   */
  public void testEmptyAnnotation() {
    try {
      annotator.annotate(new Annotation(""));
    } catch(RuntimeException e) {
      return;
    }
    fail("Never expected to get this far... the annotator should have thrown an exception by now");
  }

}
