package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.SentenceUtils;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;

/** @author John Bauer */
public class POSTaggerAnnotatorITest extends TestCase {

  private static POSTaggerAnnotator tagger; // = null;

  /**
   * Creates the tagger annotator if it isn't already created.
   */
  @Override
  public void setUp() throws Exception {
    synchronized(POSTaggerAnnotatorITest.class) {
      if (tagger == null) {
        tagger = new POSTaggerAnnotator(false);
      }
    }
  }

  /**
   * Helper method: turn an array of strings into a list of CoreLabels
   */
  private static List<CoreLabel> makeSentence(String sentence) {
    String[] words = sentence.split(" ");
    return SentenceUtils.toCoreLabelList(words);

  }

  private static CoreMap makeSentenceCoreMap(String sentence) {
    List<CoreLabel> tokens = makeSentence(sentence);
    CoreMap map = new ArrayCoreMap(1);
    map.set(CoreAnnotations.TokensAnnotation.class, tokens);
    return map;
  }

  /**
   * Helper method: check that the CoreLabels in the give sentence
   * have the expected tags
   */
  private static void checkLabels(List<CoreLabel> sentence,
                                  String... tags) {
    assertEquals(tags.length, sentence.size());
    for (int i = 0; i < tags.length; ++i) {
      assertEquals(tags[i], sentence.get(i).get(CoreAnnotations.PartOfSpeechAnnotation.class));
    }
  }

  private static void checkLabels(CoreMap sentence, String... tags){
    checkLabels(sentence.get(CoreAnnotations.TokensAnnotation.class), tags);
  }

  private static final String[] testSentences = {"My dog is fluffy and white .",
                                         "This is a second sentence .",
                                         "This sentence is only used in the threaded test .",
                                         "The Flyers have had frequent defensive breakdowns in recent games .",
                                         "Every time they are about to reach .500 , they lose another game ."};

  private static final String shortText = testSentences[0];
  private static final String longText = testSentences[0] + '\n' + testSentences[1];

  /**
   * Test that the tagger correctly handles getting a single sentence
   * in the WordsPLAnnotation
   */
  public void testWordsPLAnnotation() {
    CoreMap sent = makeSentenceCoreMap(testSentences[0]);
    List<CoreMap> sentences = new ArrayList<>();
    sentences.add(sent);

    Annotation annotation = new Annotation(shortText);
    annotation.set(CoreAnnotations.SentencesAnnotation.class, sentences );

    tagger.annotate(annotation);

    checkLabels(sent, "PRP$", "NN", "VBZ", "JJ", "CC", "JJ", ".");
  }

  /**
   * Test that it also works when you give it multiple sentences
   */
  public void testMultipleWordsPLAnnotation() {
    CoreMap firstLabels = makeSentenceCoreMap(testSentences[0]);
    CoreMap secondLabels = makeSentenceCoreMap(testSentences[1]);
    List<CoreMap> sentences = new ArrayList<>();
    sentences.add(firstLabels);
    sentences.add(secondLabels);

    Annotation annotation = new Annotation(longText);
    annotation.set(CoreAnnotations.SentencesAnnotation.class, sentences);

    tagger.annotate(annotation);

    checkLabels(firstLabels, "PRP$", "NN", "VBZ", "JJ", "CC", "JJ", ".");
    checkLabels(secondLabels, "DT", "VBZ", "DT", "JJ", "NN", ".");
  }

  /**
   * Test that a single sentence works for the SentenceAnnotation.
   */
  public void testSentencesAnnotation() {
    List<CoreLabel> labels = makeSentence(testSentences[0]);

    CoreMap sentence = new ArrayCoreMap();
    sentence.set(CoreAnnotations.TokensAnnotation.class, labels);
    List<CoreMap> sentences = new ArrayList<>();
    sentences.add(sentence);

    Annotation annotation = new Annotation(shortText);
    annotation.set(CoreAnnotations.SentencesAnnotation.class, sentences);

    tagger.annotate(annotation);

    checkLabels(labels, "PRP$", "NN", "VBZ", "JJ", "CC", "JJ", ".");
  }

  /**
   * Test that multiple sentences work for the SentenceAnnotation.
   */
  public void testMultipleSentencesAnnotation() {
    List<CoreLabel> firstLabels = makeSentence(testSentences[0]);
    List<CoreLabel> secondLabels = makeSentence(testSentences[1]);

    CoreMap firstSentence = new ArrayCoreMap();
    firstSentence.set(CoreAnnotations.TokensAnnotation.class, firstLabels);
    CoreMap secondSentence = new ArrayCoreMap();
    secondSentence.set(CoreAnnotations.TokensAnnotation.class, secondLabels);
    List<CoreMap> sentences = new ArrayList<>();
    sentences.add(firstSentence);
    sentences.add(secondSentence);

    Annotation annotation = new Annotation(longText);
    annotation.set(CoreAnnotations.SentencesAnnotation.class, sentences);

    tagger.annotate(annotation);

    checkLabels(firstLabels, "PRP$", "NN", "VBZ", "JJ", "CC", "JJ", ".");
    checkLabels(secondLabels, "DT", "VBZ", "DT", "JJ", "NN", ".");
  }

  private static Annotation makeAnnotation(String... testText) {
    List<CoreMap> sentences = new ArrayList<>();
    for (String text : testText) {
      List<CoreLabel> labels = makeSentence(text);
      CoreMap sentence = new ArrayCoreMap();
      sentence.set(CoreAnnotations.TokensAnnotation.class, labels);
      sentences.add(sentence);
    }
    Annotation annotation = new Annotation(StringUtils.join(testText));
    annotation.set(CoreAnnotations.SentencesAnnotation.class, sentences);
    return annotation;
  }

  /**
   * Check that tagging multiple sentences with different numbers of threads works
   */
  public void testMulticoreAnnotation() {
    // Just in case, since we want to manipulate the internal state
    Properties props = new Properties();

    POSTaggerAnnotator localTagger = new POSTaggerAnnotator("pos", props);
    Annotation ann = makeAnnotation(testSentences);
    localTagger.annotate(ann);
    Annotation shortAnn = makeAnnotation(testSentences[0], testSentences[1]);
    localTagger.annotate(shortAnn);

    props.setProperty("nthreads", "4");
    localTagger = new POSTaggerAnnotator("pos", props);
    Annotation ann2 = makeAnnotation(testSentences);
    localTagger.annotate(ann2);
    Annotation shortAnn2 = makeAnnotation(testSentences[0], testSentences[1]);
    localTagger.annotate(shortAnn2);

    assertEquals(ann, ann2);
    assertEquals(shortAnn, shortAnn2);

    // just to make sure it would have identified incorrect tags
    shortAnn.get(CoreAnnotations.SentencesAnnotation.class).get(0).get(CoreAnnotations.TokensAnnotation.class).get(0).set(CoreAnnotations.PartOfSpeechAnnotation.class, "foo");
    assertFalse(shortAnn.equals(shortAnn2));
  }

  /**
   * Test that if there are no annotations at all, the annotator
   * throws an exception.  We are happy if we can catch an exception
   * and continue, and if we don't get any exceptions, we throw an
   * exception of our own.
   */
  public void testEmptyAnnotation() {
    try {
      tagger.annotate(new Annotation(""));
    } catch(RuntimeException e) {
      return;
    }
    throw new RuntimeException("Never expected to get this far... the annotator should have thrown an exception by now");
  }
}


