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

///// Failing TEST
//  @Test
//  public void testSetFromStringWord() {
//    WordLemmaTag wLT = new WordLemmaTag();
//    wLT.setFromString("running");
//
//    checkWordLemmaTag(wLT, "running", null, null);
//  }
}