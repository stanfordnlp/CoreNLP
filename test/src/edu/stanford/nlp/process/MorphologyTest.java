package edu.stanford.nlp.process;

import junit.framework.TestCase;

import edu.stanford.nlp.ling.*;
import static edu.stanford.nlp.process.Morphology.*;


public class MorphologyTest extends TestCase {

  private String[] exWords = {"brethren", "ducks", "saw", "saw",
                              "running", "making", "makking",
                              "stopped", "xopped",
                              "cleaner", "cleaner", "took", "bought",
                              "am", "were", "did", "n't", "wo",
                              "'s", "'s", "ca", "her", "her", "their",
                              "Books", "light-weight", "cease-fire",
                              "John_William_Smith", "Dogs",
                              "were", "AM", "'d", "'s", "'s", "ai",
                              "sha", "them", "US",
                              "Am", "AM", "ARE", "Was", "WERE", "was",
                              "played", "PLAYED",
                              "<br>", "-0800", "an", "out-rode", "viii",
                              "b-", "s", "hath", "'ll", "d",
                              "re", "no", "r", "du",
  };

  private String[] exTags = { "NNS", "NNS", "VBD", "NN",
                              "VBG", "VBG", "VBG",
                              "VBD", "VBD",
                              "NN", "JJR", "VBD", "VBD",
                              "VBP", "VBD", "VBD", "RB", "MD",
                              "VBZ", "POS", "MD", "PRP", "PRP$", "PRP$",
                              "NNPS", "JJ", "NN",
                              "NNP", "NNS",
                              "VBD", "VBP", "MD", "VBZ", "POS", "VBP",
                              "MD", "PRP", "PRP",
                              "VBP", "VBP", "VBP", "VBD", "VBD", "VBD",
                              "VBD", "VBD",
                              "SYM", "CD", "DT", "VBD", "FW",
                              "AFX", "VBZ", "VBP", "MD", "MD",
                              "VBP", "VBP", "VBP", "VBP",
  };

  private String[] exAnswers = {"brethren", "duck", "see", "saw",
                                "run", "make", "makk",
                                "stop", "xopp",
                                "cleaner", "cleaner", "take", "buy",
                                "be", "be", "do", "not", "will",
                                "be", "'s", "can", "she", "she", "they",
                                "Books", "light-weight", "cease-fire",
                                "John_William_Smith", "dog",
                                "be", "be", "would", "be", "'s", "be",
                                "shall", "they", "we",
                                "be", "be", "be", "be", "be", "be",
                                "play", "play",
                                "<br>", "-0800", "a", "out-ride", "viii",
                                "b-", "be", "have", "will", "would",
                                "be", "know", "be", "do",
  };

  public void testMorph() {
    assert(exWords.length == exTags.length);
    assert(exWords.length == exAnswers.length);
    for (int i = 0; i < exWords.length; i++) {
      WordLemmaTag ans = lemmatizeStatic(new WordTag(exWords[i], exTags[i]));
      assertEquals("Stemmed " + exWords[i] + '/' + exTags[i] + " to lemma " +
                   ans.lemma() + " versus correct " + exAnswers[i],
                   ans.lemma(), exAnswers[i]);
    }
  }

  public void testStem() {
    assertEquals("John", stemStatic(new WordTag("John", "NNP")).word());
    assertEquals("Corporations", stemStatic(new WordTag("Corporations", "NNPS")).word());
    WordTag hunt = new WordTag("hunting", "V");
    assertEquals("hunt", stemStatic(hunt).word());
    assertEquals("hunt", lemmatizeStatic(hunt).lemma());
  }

  public void testDunno() {
    assertEquals("do", stemStatic(new WordTag("du", "VBP")).word());
    assertEquals("not", stemStatic(new WordTag("n", "RB")).word());
    assertEquals("know", stemStatic(new WordTag("no", "VB")).word());
  }

  public void testDash() {
    Morphology morpha = new Morphology();
    morpha.stem("b-");
  }

  public void testStemStatic() {
    WordTag wt2 = new WordTag("objecting", "VBG");
    WordTag wt = Morphology.stemStatic(wt2);
    assertEquals("object", wt.word());
    wt2 = new WordTag("broken", "VBN");
    wt = Morphology.stemStatic(wt2);
    assertEquals("break", wt.word());
    wt2 = new WordTag("topoi", "NNS");
    wt = Morphology.stemStatic(wt2);
    assertEquals("topos", wt.word());
    wt2 = new WordTag("radii", "NNS");
    wt = Morphology.stemStatic(wt2);
    assertEquals("radius", wt.word());
  }

}
