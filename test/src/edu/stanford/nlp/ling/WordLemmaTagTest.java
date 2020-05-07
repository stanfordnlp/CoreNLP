package edu.stanford.nlp.ling;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WordLemmaTagTest {

  private void checkWordLemmaTag(WordLemmaTag wLT, String word, String lemma, String tag) {
    assertEquals("Incorrect word", word, wLT.word());
    assertEquals("Incorrect lemma", lemma, wLT.lemma());
    assertEquals("Incorrect tag", tag, wLT.tag());
  }

  @Test
  public void testWordLemmaConstructorLabelWord() {
    WordLemmaTagFactory wLtF = new WordLemmaTagFactory();
    Label label = wLtF.newLabel("running");

    WordLemmaTag wLt = new WordLemmaTag(label);

    checkWordLemmaTag(wLt, "running", null, null);
  }

  @Test
  public void testWordLemmaConstructorLabelWordLabelTag() {
    WordLemmaTagFactory wLtF = new WordLemmaTagFactory();
    Label labelWord = wLtF.newLabel("running");
    Label labelTag = wLtF.newLabel("r");

    WordLemmaTag wLt = new WordLemmaTag(labelWord, labelTag);

    checkWordLemmaTag(wLt, "running", "running", "r");
  }

  @Test
  public void testWordLemmaConstructorWordTag() {
    String word = "run";
    String tag = "r";
    WordLemmaTag wLt = new WordLemmaTag(word, tag);

    checkWordLemmaTag(wLt, "run", "run", "r");
  }

  @Test
  public void testSetFromStringWordAndLemma() {
    WordLemmaTag wLT = new WordLemmaTag();
    wLT.setFromString("running/r");

    checkWordLemmaTag(wLT, "running", "running", "r");
  }

  @Test
  public void testSetFromStringWordAndLemmaAndTag() {
    WordLemmaTag wLT = new WordLemmaTag();
    wLT.setFromString("studying/study/s");

    checkWordLemmaTag(wLT, "studying", "study", "s");
  }

  @Test
  public void testSetFromStringWord() {
    WordLemmaTag wLT = new WordLemmaTag();
    wLT.setFromString("running");

    checkWordLemmaTag(wLT, "running", null, null);
  }
} 
