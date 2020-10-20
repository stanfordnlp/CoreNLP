package edu.stanford.nlp.ling;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WordLemmaTagFactoryTest {

  private void checkWordLemmaTag(WordLemmaTag wLT, String word, String lemma, String tag) {
    assertEquals("Incorrect word", word, wLT.word());
    assertEquals("Incorrect lemma", lemma, wLT.lemma());
    assertEquals("Incorrect tag", tag, wLT.tag());
  }

  @Test
  public void newLabelWord() {
    WordLemmaTagFactory wTf = new WordLemmaTagFactory();
    Label label = wTf.newLabel("running");

    WordLemmaTag wLt = (WordLemmaTag) label;

    checkWordLemmaTag(wLt, "running", null, null);
  }

  @Test
  public void newLabelLemma() {
    WordLemmaTagFactory wTf = new WordLemmaTagFactory();
    Label label = wTf.newLabel("run", WordLemmaTagFactory.LEMMA_LABEL);

    WordLemmaTag wLt = (WordLemmaTag) label;

    checkWordLemmaTag(wLt, null, "run", null);
  }

  @Test
  public void newLabelTag() {
    WordLemmaTagFactory wTf = new WordLemmaTagFactory();
    Label label = wTf.newLabel("t", WordLemmaTagFactory.TAG_LABEL);

    WordLemmaTag wLt = (WordLemmaTag) label;

    checkWordLemmaTag(wLt, null, null, "t");
  }

  @Test
  public void newLabelFromStringWordLemmaTag() {
    WordLemmaTagFactory wTf = new WordLemmaTagFactory();
    Label label = wTf.newLabelFromString("running/run/r");

    WordLemmaTag wLt = (WordLemmaTag) label;

    checkWordLemmaTag(wLt, "running", "run", "r");
  }

  @Test
  public void newLabelFromStringWordTag() {
    WordLemmaTagFactory wTf = new WordLemmaTagFactory();
    Label label = wTf.newLabelFromString("running/r");

    WordLemmaTag wLt = (WordLemmaTag) label;

    checkWordLemmaTag(wLt, "running", "running", "r");
  }

  @Test
  public void newLabelFromStringWord() {
    WordLemmaTagFactory wTf = new WordLemmaTagFactory();
    Label label = wTf.newLabelFromString("run");

    WordLemmaTag wLt = (WordLemmaTag) label;

    checkWordLemmaTag(wLt, "run", null, null);
  }
}
