package edu.stanford.nlp.process;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import edu.stanford.nlp.ling.Word;

public class StopListTest {

  @Test
  public void testGenericWordConstructor() {
    // Tests that no exception being thrown during instantiation
    StopList sut = new StopList();
  }

  @Test
  public void testGenericWordConstainsString() {
    // Tests that a common stopword is contained in the list as string
    StopList sut = new StopList();
    boolean containsWord = sut.contains("and");
    assertTrue(containsWord);
  }

  @Test
  public void testGenericWordConstainsWord() {
    // Tests that a common stopword is contained in the list as Word object
    StopList sut = new StopList();
    Word testinput = new Word("or");
    boolean containsWord = sut.contains(testinput);
    assertTrue(containsWord);
  }

}
