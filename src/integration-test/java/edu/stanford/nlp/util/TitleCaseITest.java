package edu.stanford.nlp.util;

import junit.framework.TestCase;

public class TitleCaseITest extends TestCase {

  public void testTitleCaseCheck() {
    assertTrue(StringUtils.isTitleCase("Title"));
    assertTrue(StringUtils.isTitleCase("Hello World"));
    assertTrue(StringUtils.isTitleCase("Hello\tWorld"));
    assertTrue(StringUtils.isTitleCase("Hello \t World"));
    assertFalse(StringUtils.isTitleCase("hello world"));
    assertFalse(StringUtils.isTitleCase("Hello world"));
    assertFalse(StringUtils.isTitleCase("HELLO WORLD"));
  }

  public void testToTitleCase() {
    assertTrue("Hello World".equals(StringUtils.toTitleCase("hello world")));
    assertTrue("Hello World ".equals(StringUtils.toTitleCase("HELLO WORLD ")));
  }

}
