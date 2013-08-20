package edu.stanford.nlp.ling;

import junit.framework.TestCase;

public class CategoryWordTagTest extends TestCase {
  public void testCopy() {
    CategoryWordTag tag = new CategoryWordTag("A", "B", "C");
    assertEquals("A", tag.category());
    assertEquals("B", tag.word());
    assertEquals("C", tag.tag());

    CategoryWordTag tag2 = new CategoryWordTag(tag);
    assertEquals("A", tag2.category());
    assertEquals("B", tag2.word());
    assertEquals("C", tag2.tag());
  }
}
