package edu.stanford.nlp.objectbank;

// Copyright 2010, Stanford NLP
// Author: John Bauer

// Test that the XMLBeginEndIterator will successfully find a bunch of
// text inside xml tags.

// TODO: can add tests for the String->Object conversion and some of
// the other options the XMLBeginEndIterator has

import junit.framework.TestCase;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;

public class XMLBeginEndIteratorTest extends TestCase {

  public static final String TEST_STRING = "<xml><tagger>\n  <text>\n    This tests the xml input.\n  </text>  \n  This should not be found.  \n  <text>\n    This should be found.\n  </text>\n  <text>\n    The dog's barking kept the\n neighbors up all night.\n  </text>\n</tagging></xml>";

  public static final String EMPTY_TEST_STRING = "<text></text>";

  public static final String SINGLE_TAG_TEST_STRING = "<xml><text>This tests the xml input with single tags<text/>, which should not close the input</text><text/>and should not open it either.</xml>";

  public static final String NESTING_TEST_STRING = "<xml><text>A<text>B</text>C</text>D <text>A<text>B</text>C<text>D</text>E</text>F <text>A<text>B</text>C<text>D<text/></text>E</text>F</xml>";

  public static final String TAG_IN_TEXT_STRING = "<xml><bar>The dog's barking kept the neighbors up all night</bar></xml>";

  public static final String TWO_TAGS_STRING = "<xml><foo>This is the first sentence</foo><bar>The dog's barking kept the neighbors up all night</bar><foo>The owner could not stop the dog from barking</foo></xml>";

  public static ArrayList<String> getResults(XMLBeginEndIterator<String> iterator) {
    ArrayList<String> results = new ArrayList<>();
    while (iterator.hasNext()) {
      results.add(iterator.next());
    }
    return results;
  }

  public static void compareResults(XMLBeginEndIterator<String> iterator,
                                    String... expectedResults) {
    ArrayList<String> results = getResults(iterator);
    assertEquals(expectedResults.length, results.size());
    for (int i = 0; i < expectedResults.length; ++i) {
      assertEquals(expectedResults[i], results.get(i));
    }
  }

  public void testNotFound() {
    XMLBeginEndIterator<String> iterator = new XMLBeginEndIterator<>(new BufferedReader(new StringReader(TEST_STRING)), "zzzz");
    compareResults(iterator);    // eg, should be empty
  }

  public void testFound() {
    XMLBeginEndIterator<String> iterator = new XMLBeginEndIterator<>(new BufferedReader(new StringReader(TEST_STRING)), "text");
    compareResults(iterator,
                   "\n    This tests the xml input.\n  ",
                   "\n    This should be found.\n  ",
                   "\n    The dog's barking kept the\n neighbors up all night.\n  ");
  }

  public void testEmpty() {
    XMLBeginEndIterator<String> iterator = new XMLBeginEndIterator<>(new BufferedReader(new StringReader(EMPTY_TEST_STRING)), "text");
    compareResults(iterator, "");
  }

  public void testSingleTags() {
    XMLBeginEndIterator<String> iterator = new XMLBeginEndIterator<>(new BufferedReader(new StringReader(SINGLE_TAG_TEST_STRING)), "text");
    compareResults(iterator,
                   "This tests the xml input with single tags, which should not close the input");
  }

  public void testNesting() {
    XMLBeginEndIterator<String> iterator = new XMLBeginEndIterator<>(new BufferedReader(new StringReader(NESTING_TEST_STRING)), "text",
                                                                           false, false, true);
    compareResults(iterator,
                   "ABC", "ABCDE", "ABCDE");
  }

  public void testInternalTags() {
    XMLBeginEndIterator<String> iterator = new XMLBeginEndIterator<>(new BufferedReader(new StringReader(NESTING_TEST_STRING)), "text",
                                                                           true, false, true);
    compareResults(iterator,
                   "A<text>B</text>C",
                   "A<text>B</text>C<text>D</text>E",
                   "A<text>B</text>C<text>D<text/></text>E");
  }

  public void testContainingTags() {
    XMLBeginEndIterator<String> iterator = new XMLBeginEndIterator<>(new BufferedReader(new StringReader(NESTING_TEST_STRING)), "text",
                                                                           true, true, true);
    compareResults(iterator,
                   "<text>A<text>B</text>C</text>",
                   "<text>A<text>B</text>C<text>D</text>E</text>",
                   "<text>A<text>B</text>C<text>D<text/></text>E</text>");
  }

  public void testTagInText() {
    XMLBeginEndIterator<String> iterator = new XMLBeginEndIterator<>(new BufferedReader(new StringReader(TAG_IN_TEXT_STRING)), "bar");
    compareResults(iterator,
                   "The dog's barking kept the neighbors up all night");
  }

  public void testTwoTags() {
    XMLBeginEndIterator<String> iterator = new XMLBeginEndIterator<>(new BufferedReader(new StringReader(TWO_TAGS_STRING)), "foo|bar");
    compareResults(iterator,
                   "This is the first sentence",
                   "The dog's barking kept the neighbors up all night",
                   "The owner could not stop the dog from barking");
  }

}
