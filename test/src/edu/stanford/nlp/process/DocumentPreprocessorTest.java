package edu.stanford.nlp.process;

// Copyright 2010, Stanford NLP
// Author: John Bauer

// Test the effects of various operations using a DocumentPreprocessor.

import junit.framework.TestCase;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Sentence;


public class DocumentPreprocessorTest extends TestCase {
  // Test to see if it is correctly splitting text readers
  public void testText() {
    String test = "This is a test of the preprocessor2.  It should split this text into sentences.  I like resting my feet on my desk.  Hopefully the people around my office don't hear me singing along to my music, and if they do, hopefully they aren't annoyed.  My test cases are probably terrifying looks into my psyche.";
    String []expectedResults = {"This is a test of the preprocessor2 .",
                                "It should split this text into sentences .",
                                "I like resting my feet on my desk .",
                                "Hopefully the people around my office do n't hear me singing along to my music , and if they do , hopefully they are n't annoyed .",
                                "My test cases are probably terrifying looks into my psyche ."};
    ArrayList<String> results = new ArrayList<String>();
    DocumentPreprocessor document =
      new DocumentPreprocessor(new BufferedReader(new StringReader(test)));
    for (List<HasWord> sentence : document) {
      results.add(Sentence.listToString(sentence));
    }

    assertEquals(expectedResults.length, results.size());
    for (int i = 0; i < results.size(); ++i) {
      assertEquals(expectedResults[i], results.get(i));
    }
  }

  public static void compareXMLResults(String input,
                                String element,
                                String ... expectedResults) {
    ArrayList<String> results = new ArrayList<String>();
    DocumentPreprocessor document = new DocumentPreprocessor(new BufferedReader(new StringReader(input)), DocumentPreprocessor.DocType.XML);
    document.setElementDelimiter(element);
    for (List<HasWord> sentence : document) {
      results.add(Sentence.listToString(sentence));
    }

    assertEquals(expectedResults.length, results.size());
    for (int i = 0; i < results.size(); ++i) {
      assertEquals(expectedResults[i], results.get(i));
    }
  }

  String BASIC_XML_TEST = "<xml><text>The previous test was a lie.  I didn't make this test in my office; I made it at home.</text>\nMy home currently smells like dog vomit.\n<text apartment=\"stinky\">My dog puked everywhere after eating some carrots the other day.\n  Hopefully I have cleaned the last of it, though.</text>\n\nThis tests to see what happens on an empty tag:<text></text><text>It shouldn't include a blank sentence, but it should include this sentence.</text>this is madness...<text>no, this <text> is </text> NESTED!</text>This only prints 'no, this is' instead of 'no, this is NESTED'.  Doesn't do what i would expect, but it's consistent with the old behavior.</xml>";


  // Tests various ways of finding sentences with an XML
  // DocumentPreprocessor2.  We test to make sure it does find the
  // text between <text> tags and that it doesn't find any text if we
  // look for <zzzz> tags.
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

  // Yeah... a bug that failed this test bug not the NotInText test
  // was part of the preprocessor for a while.
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

  public void testWhitespaceTokenization() {
    String test = "This is a whitespace tokenized test case . \n  This should be the second sentence    . \n \n  \n\n  This should be the third sentence .  \n  This should be one sentence . The period should not break it . \n This is the fifth sentence , with a weird period at the end.";

    String[] expectedResults = {"This is a whitespace tokenized test case .",
                                "This should be the second sentence .",
                                "This should be the third sentence .",
            "This should be one sentence . The period should not break it .",
            "This is the fifth sentence , with a weird period at the end."};


    ArrayList<String> results = new ArrayList<String>();
    DocumentPreprocessor document =
      new DocumentPreprocessor(new BufferedReader(new StringReader(test)));
    document.setTokenizerFactory(null);
    document.setSentenceDelimiter("\n");
    for (List<HasWord> sentence : document) {
      results.add(Sentence.listToString(sentence));
    }

    assertEquals(expectedResults.length, results.size());
    for (int i = 0; i < results.size(); ++i) {
      assertEquals(expectedResults[i], results.get(i));
    }
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

