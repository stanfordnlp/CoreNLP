package edu.stanford.nlp.pipeline;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;

/**
 * Tests a short pipeline starting from raw text and finishing with
 * the MorphaAnnotator.  Checks the output to make sure it is as
 * expected.
 *
 * @author Heeyoung Lee
 * @author John Bauer
 */
public class MorphaAnnotatorITest {

  private static AnnotationPipeline fullPipeline;
  private static AnnotationPipeline shortPipeline;

  @Before
  public void setUp() throws Exception {
    synchronized(MorphaAnnotatorITest.class) {
      if (fullPipeline == null) {
        fullPipeline = new AnnotationPipeline();
        fullPipeline.addAnnotator(new TokenizerAnnotator(false, "en"));
        fullPipeline.addAnnotator(new WordsToSentencesAnnotator(false));
        fullPipeline.addAnnotator(new POSTaggerAnnotator(false));
        fullPipeline.addAnnotator(new MorphaAnnotator(false));
      }

      if (shortPipeline == null) {
        shortPipeline = new AnnotationPipeline();
        shortPipeline.addAnnotator(new MorphaAnnotator(false));
      }
    }
  }

  private static void checkResult(List<CoreLabel> words) {
    Assert.assertEquals(words.size(), answer.length);

    for (int i = 0 ; i < answer.length ; i++){
      CoreLabel word = words.get(i);
      String lemma = word.get(CoreAnnotations.LemmaAnnotation.class);
      Assert.assertEquals(lemma, answer[i]);
    }
  }

  @Test
  public void testMorphaAnnotator() {
    Annotation document = new Annotation(text);
    fullPipeline.annotate(document);
    checkResult(document.get(CoreAnnotations.TokensAnnotation.class));
  }

  private static List<CoreLabel> getTestWords() {
    List<CoreLabel> words = new ArrayList<>();
    if (tokenizedText.length != tokenizedTags.length) {
      throw new AssertionError("tokenizedText and tokenizedTags " +
                               "must be of the same length");
    }
    for (int i = 0; i < tokenizedText.length; ++i) {
      CoreLabel word = new CoreLabel();
      word.setWord(tokenizedText[i]);
      word.set(CoreAnnotations.TextAnnotation.class, tokenizedText[i]);
      word.setTag(tokenizedTags[i]);
      words.add(word);
    }
    return words;
  }

  @Test
  public void testSentencesAnnotation() {
    List<CoreLabel> words = getTestWords();

    CoreMap sentence = new ArrayCoreMap();
    sentence.set(CoreAnnotations.TokensAnnotation.class, words);
    List<CoreMap> sentences = new ArrayList<>();
    sentences.add(sentence);
    Annotation document = new Annotation(text);
    document.set(CoreAnnotations.SentencesAnnotation.class, sentences);

    shortPipeline.annotate(document);
    checkResult(words);
  }

  private static final String text = "I saw him ordering a saw. Jack 's father has n't played\ngolf since 20 years ago . I 'm going to the\nbookstore to return a book Jack and his friends bought me .";

  private static final String[] answer = {
          "I", "see", "he", "order", "a", "saw", ".", "Jack", "'s",
          "father", "have", "not", "play", "golf", "since", "20", "year", "ago",
          ".", "I", "be", "go", "to", "the", "bookstore", "to", "return", "a",
          "book", "Jack", "and", "he", "friend", "buy", "I", "."
  };

  private static final String[] tokenizedText = {
          "I", "saw", "him", "ordering", "a", "saw", ".", "Jack", "'s",
          "father", "has", "n't", "played", "golf", "since", "20", "years", "ago",
          ".", "I", "'m", "going", "to", "the", "bookstore", "to", "return", "a",
          "book", "Jack", "and", "his", "friends", "bought", "me", "."
  };

  private static final String[] tokenizedTags = {
          "PRP", "VBD", "PRP", "VBG", "DT", "NN", ".", "NNP", "POS",
          "NN", "VBZ", "RB", "VBN", "NN", "IN", "CD", "NNS", "RB",
          ".", "PRP", "VBP", "VBG", "TO", "DT", "NN", "TO", "VB", "DT",
          "NN", "NNP", "CC", "PRP$", "NNS", "VBD", "PRP", "."
  };

}


