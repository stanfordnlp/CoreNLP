package edu.stanford.nlp.util;

// Copyright 2010, Stanford NLP
// Author: John Bauer

// So far, this test only tests the stripTags() method.

// TODO: test everything else

import junit.framework.TestCase;

import java.io.BufferedReader;
import java.io.StringReader;

public class XMLUtilsTest extends TestCase {
  public void testStripTags() {
    String text = "<song><lyrics>Do you think I'm special</lyrics><br><lyrics>Do you think I'm nice</lyrics><br><lyrics whining=\"excessive\">Am I bright enough to shine in your spaces?</lyrics></song>";

    String expectedBreakingResult = "Do you think I'm special\nDo you think I'm nice\nAm I bright enough to shine in your spaces?";
    String result = XMLUtils.stripTags(new BufferedReader(new StringReader(text)), null, true);
    assertEquals(expectedBreakingResult, result);

    String expectedNoBreakingResult = "Do you think I'm specialDo you think I'm niceAm I bright enough to shine in your spaces?";
    result = XMLUtils.stripTags(new BufferedReader(new StringReader(text)), null, false);
    assertEquals(expectedNoBreakingResult, result);
  }

  public void testXMLTag() {
    XMLUtils.XMLTag foo = new XMLUtils.XMLTag("<br />");
    assertEquals("br", foo.name);
    assertTrue(foo.isSingleTag);

    foo = new XMLUtils.XMLTag("<List  name  =   \"Fruit List\"    >");
    assertEquals("List", foo.name);
    assertFalse(foo.isSingleTag);
    assertFalse(foo.isEndTag);
    assertEquals("Fruit List", foo.attributes.get("name"));

    foo = new XMLUtils.XMLTag("</life  >");
    assertEquals("life", foo.name);
    assertTrue(foo.isEndTag);
    assertFalse(foo.isSingleTag);
    assertTrue(foo.attributes.isEmpty());

    foo = new XMLUtils.XMLTag("<P>");
    assertEquals("P", foo.name);
  }

}
