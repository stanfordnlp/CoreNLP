package edu.stanford.nlp.process;

import junit.framework.TestCase;

import edu.stanford.nlp.ling.*;
import static edu.stanford.nlp.process.Morphology.*;


public class MorphologyTest extends TestCase {

  private String[] exWords = {"brethren", "ducks", "saw", "saw",
                              "running", "making", "makking",
                              "stopped", "xopped",
                              "cleaner", "cleaner", "took", "bought",
                              "am", "were", "did", "n't", "n’t", "nt", "wo",
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
                              "feces", "rights", "papers", "goggles", "vibes",
                              "K's", "K’s",
                              "'ve", "'m",
                              "ski'd",
                              "na", "to", "gon", "wan", "wan",
                              "I", "i",
                              "better", "gooier", "glummer", "tamer", "sicker",
                              "best", "gooiest", "glummest", "tamest", "sickest",
                              "better", "earlier", // should not change if JJ
                              "earlier", "earliest", "more", "less", "least", // RBR / RBS special cases
                              "quicker", "slower", "longer", "wider", "widest",
                              "easier", "easier", // JJR & RBR
                              "graffiti", "ABCs", "Olympics", "Olympics",
                              "Burmese", "Chinese", "Chinese",
  };

  private String[] exTags = { "NNS", "NNS", "VBD", "NN",
                              "VBG", "VBG", "VBG",
                              "VBD", "VBD",
                              "NN", "JJR", "VBD", "VBD",
                              "VBP", "VBD", "VBD", "RB", "RB", "RB", "MD",
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
                              "NNS", "NNS", "NNS", "NNS", "NNS",
                              "NNS", "NNS",
                              "VBP", "VBP",
                              "VBD",
                              "TO", "TO", "VBG", "VB", "VBP",
                              "PRP", "PRP",
                              "JJR", "JJR", "JJR", "JJR", "JJR",
                              "JJS", "JJS", "JJS", "JJS", "JJS",
                              "JJ", "JJ",
                              "RBR", "RBS", "RBR", "RBR", "RBS",
                              "RBR", "RBR", "RBR", "RBR", "RBS",
                              "JJR", "RBR",
                              "NNS", "NNS", "NNS", "NNPS",
                              "NNS", "NNS", "JJ",
  };

  private String[] exAnswers = {"brethren", "duck", "see", "saw",
                                "run", "make", "makk",
                                "stop", "xopp",
                                "cleaner", "clean", "take", "buy",
                                "be", "be", "do", "not", "not", "not", "will",
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
                                "feces", "right", "paper", "goggles", "vibe",
                                "k", "k",
                                "have", "be",
                                "ski",
                                "to", "to", "go", "want", "want",
                                "I", "I",
                                "good", "gooey", "glum", "tame", "sick",
                                "good", "gooey", "glum", "tame", "sick",
                                "better", "earlier",
                                "early", "early", "more", "less", "least",
                                "quick", "slow", "long", "wide", "wide",
                                "easy", "easy",
                                "graffito", "ABC", "Olympics", "Olympics",
                                "Burmese", "Chinese", "Chinese",
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
