package edu.stanford.nlp.process;

// Copyright 2010, Stanford University, GPLv2

import junit.framework.TestCase;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.SentenceUtils;


/** Test the effects of various operations using a DocumentPreprocessor.
 *
 *  @author John Bauer
 *  @version 2010
 */
public class DocumentPreprocessorTest extends TestCase {

  private static void runTest(String input, String[] expected) {
    runTest(input, expected, null, false);
  }

  private static void runTest(String input, String[] expected, String[] sentenceFinalPuncWords, boolean whitespaceTokenize) {
    List<String> results = new ArrayList<>();
    DocumentPreprocessor document =
      new DocumentPreprocessor(new BufferedReader(new StringReader(input)));
    if (sentenceFinalPuncWords != null) {
      document.setSentenceFinalPuncWords(sentenceFinalPuncWords);
    }
    if (whitespaceTokenize) {
      document.setTokenizerFactory(null);
      document.setSentenceDelimiter("\n");
    }
    for (List<HasWord> sentence : document) {
      results.add(SentenceUtils.listToString(sentence));
    }

    assertEquals("Should be " + expected.length + " sentences but got " + results.size() + ": " + results,
            expected.length, results.size());
    for (int i = 0; i < results.size(); ++i) {
      assertEquals("Failed on sentence " + i, expected[i], results.get(i));
    }
  }

  /** Test to see if it is correctly splitting text readers. */
  public void testText() {
    String test = "This is a test of the preprocessor2.  It should split this text into sentences.  I like resting my feet on my desk.  Hopefully the people around my office don't hear me singing along to my music, and if they do, hopefully they aren't annoyed.  My test cases are probably terrifying looks into my psyche.";
    String[] expectedResults = {"This is a test of the preprocessor2 .",
                                "It should split this text into sentences .",
                                "I like resting my feet on my desk .",
                                "Hopefully the people around my office do n't hear me singing along to my music , and if they do , hopefully they are n't annoyed .",
                                "My test cases are probably terrifying looks into my psyche ."};
    runTest(test, expectedResults);
  }

  /** Test if fails with punctuation near end. We did at one point. */
  public void testNearFinalPunctuation() {
    String test = "Mount. Annaguan";
    String[] expectedResults = {"Mount .",
                                "Annaguan", };
    runTest(test, expectedResults);
  }

  /** Test if fails with punctuation near end. We did at one point. */
  public void testNearFinalPunctuation2() {
    String test = "(I lied.)";
    String[] expectedResults = { "( I lied . )" };
    runTest(test, expectedResults);
  }

  public void testSetSentencePunctWords() {
    String test = "This is a test of the preprocessor2... it should split this text into sentences? This should be a different sentence.This should be attached to the previous sentence, though. Calvin Wilson for St. Louis Post Dispatch called it one of LaBeouf's best performances.";
    String[] expectedResults = {
            "This is a test of the preprocessor2 ...",
            "it should split this text into sentences ?",
            "This should be a different sentence.This should be attached to the previous sentence , though .",
            "Calvin Wilson for St. Louis Post Dispatch called it one of LaBeouf 's best performances .",
    };
    String[] sentenceFinalPuncWords = {".", "?","!","...","\n"};
    runTest(test, expectedResults, sentenceFinalPuncWords, false);
  }

  public void testWhitespaceTokenization() {
    String test = "This is a whitespace tokenized test case . \n  This should be the second sentence    . \n \n  \n\n  This should be the third sentence .  \n  This should be one sentence . The period should not break it . \n This is the fifth sentence , with a weird period at the end.";

    String[] expectedResults = {"This is a whitespace tokenized test case .",
                                "This should be the second sentence .",
                                "This should be the third sentence .",
            "This should be one sentence . The period should not break it .",
            "This is the fifth sentence , with a weird period at the end."};
    runTest(test, expectedResults, null, true);
  }


  private static void compareXMLResults(String input,
                                String element,
                                String ... expectedResults) {
    ArrayList<String> results = new ArrayList<>();
    DocumentPreprocessor document = new DocumentPreprocessor(new BufferedReader(new StringReader(input)), DocumentPreprocessor.DocType.XML);
    document.setElementDelimiter(element);
    for (List<HasWord> sentence : document) {
      results.add(SentenceUtils.listToString(sentence));
    }

    assertEquals(expectedResults.length, results.size());
    for (int i = 0; i < results.size(); ++i) {
      assertEquals(expectedResults[i], results.get(i));
    }
  }

  private static final String BASIC_XML_TEST = "<xml><text>The previous test was a lie.  I didn't make this test in my office; I made it at home.</text>\nMy home currently smells like dog vomit.\n<text apartment=\"stinky\">My dog puked everywhere after eating some carrots the other day.\n  Hopefully I have cleaned the last of it, though.</text>\n\nThis tests to see what happens on an empty tag:<text></text><text>It shouldn't include a blank sentence, but it should include this sentence.</text>this is madness...<text>no, this <text> is </text> NESTED!</text>This only prints 'no, this is' instead of 'no, this is NESTED'.  Doesn't do what i would expect, but it's consistent with the old behavior.</xml>";

  /** Tests various ways of finding sentences with an XML
   *  DocumentPreprocessor2.  We test to make sure it does find the
   *  text between {@code <text>} tags and that it doesn't find any text if we
   *  look for {@code <zzzz>} tags.
   */
  public void testXMLBasic() {
    // This subsequent block of code can be uncommented to demonstrate
    // that the results from the new DP are the same as from the old DP.
    //
    // System.out.println("\nThis is the old behavior\n");
    // DocumentPreprocessor p = new DocumentPreprocessor();
    // List<List<? extends HasWord>> r = p.getSentencesFromXML(new BufferedReader(new StringReader(test)), "text", null, false);
    // System.out.println(r.size());
    // for (List<? extends HasWord> s : r) {
    //   System.out.println("\"" + Sentence.listToString(s) + "\"");
    // }
    //
    // System.out.println("\nThis is the new behavior\n");
    // DocumentPreprocessor2 d = new DocumentPreprocessor2(new BufferedReader(new StringReader(test)), DocumentPreprocessor2.DocType.XML);
    // d.setElementDelimiter("text");
    // for (List<HasWord> sentence : d) {
    //   System.out.println("\"" + Sentence.listToString(sentence) + "\"");
    // }

    String []expectedResults = {"The previous test was a lie .",
                                "I did n't make this test in my office ; I made it at home .",
                                "My dog puked everywhere after eating some carrots the other day .",
                                "Hopefully I have cleaned the last of it , though .",
                                "It should n't include a blank sentence , but it should include this sentence .",
                                "no , this is"};

    compareXMLResults(BASIC_XML_TEST, "text", expectedResults);
  }

  public void testXMLNoResults() {
    compareXMLResults(BASIC_XML_TEST, "zzzz");
  }

  /** Yeah... a bug that failed this test bug not the NotInText test
   *  was part of the preprocessor for a while.
   */
  public void testXMLElementInText() {
    String TAG_IN_TEXT = "<xml><wood>There are many trees in the woods</wood></xml>";
    compareXMLResults(TAG_IN_TEXT, "wood",
                      "There are many trees in the woods");
  }

  public void testXMLElementNotInText() {
    String TAG_IN_TEXT = "<xml><wood>There are many trees in the forest</wood></xml>";
    compareXMLResults(TAG_IN_TEXT, "wood",
                      "There are many trees in the forest");
  }


  public void testPlainTextIterator() {
    String test = "This is a one line test . \n";
    String[] expectedResults = {"This", "is", "a", "one", "line", "test", "."};

    DocumentPreprocessor document =
      new DocumentPreprocessor(new BufferedReader(new StringReader(test)));
    document.setTokenizerFactory(null);
    document.setSentenceDelimiter("\n");

    Iterator<List<HasWord>> iterator = document.iterator();
    // we test twice because this call should not eat any text
    assertTrue(iterator.hasNext());
    assertTrue(iterator.hasNext());
    List<HasWord> words = iterator.next();
    assertEquals(expectedResults.length, words.size());
    for (int i = 0; i < expectedResults.length; ++i) {
      assertEquals(expectedResults[i], words.get(i).word());
    }
    // we test twice to make sure we don't blow up on multiple calls
    assertFalse(iterator.hasNext());
    assertFalse(iterator.hasNext());

    try {
      iterator.next();
      throw new AssertionError("iterator.next() should have blown up");
    } catch (NoSuchElementException e) {
      // yay, this is what we want
    }

    // just in case
    assertFalse(iterator.hasNext());
  }

}

