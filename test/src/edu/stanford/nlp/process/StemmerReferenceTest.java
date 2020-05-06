package edu.stanford.nlp.process;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Random;
import java.util.Arrays;

import org.junit.Test;

public class StemmerReferenceTest {
  private Stemmer sut = new edu.stanford.nlp.process.Stemmer();
  private Random rand = new Random();
  private List<String> wordsToTest = Arrays.asList(
    "anxiously",
    "biggest",
    "big",
    "doable",
    "illiterate",
    "tentative",
    "mattress",
    "mattresses",
    "disabled",
    "ponies",
    "men",
    "words",
    "doing",
    "do",
    "make",
    "Pneumonoultramicroscopicsilicovolcanoconiosis",
    "stupid",
    "John",
    "and",
    "or",
    "on",
    "at",
    "by",
    "the",
    "her",
    "him",
    "them",
    "they",
    "not");

  /* Testing against Martin Porters reference implementation */
  @Test
  public void testReferenceStemming() {
    List<String> wordsToFuzzOver = wordsToTest;

    for(String word: wordsToFuzzOver) {
      this.compareWordAcrossImplementations(word);
    }
  }

  /* Testing against Martin Porters reference implementation */
  @Test
  public void testReferenceStemmingFuzzed() {
    List<String> wordsToFuzzOver = wordsToTest;
    
    for(String word: wordsToFuzzOver) {
      String fuzzedWord = this.fuzzWord(word);
      this.compareWordAcrossImplementations(fuzzedWord);
    }
  }
  
  /*
   * Replaces a random character of the word
   */
  private String fuzzWord (String word) {
    if(word.length() == 0) {
      return word;
    }
    int randomIndex = rand.nextInt(word.length());
    char randomChar = (char)(rand.nextInt(26) + 'a');
    char currentChar = word.charAt(randomIndex);
    return word.replace(currentChar, randomChar);
  }

  private void compareWordAcrossImplementations(String word) {
    // With the reference impl we need a new stemmer for each word
    edu.stanford.nlp.process.StemmerReferenceImplementation.Stemmer referenceStemmer = new StemmerReferenceImplementation.Stemmer();
    char[] chars = word.toCharArray();

    for (char ch : chars) {
      referenceStemmer.add(ch);
    }
    referenceStemmer.stem();
    String referenceResult = referenceStemmer.toString();
    String sutResult = sut.stem(word);
    assertEquals("Stems for '" + word + "' should match", referenceResult, sutResult);
  }
}
