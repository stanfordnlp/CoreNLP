package edu.stanford.nlp.ling;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class CategoryWordTagTest {
  @Test
  public void testCopy() {
    CategoryWordTag tag = new CategoryWordTag("A", "B", "C");
    CategoryWordTag tag2 = new CategoryWordTag(tag);
    assertEquals("Copy category", "A", tag2.category());
    assertEquals("Copy word", "B", tag2.word());
    assertEquals("Copy tag", "C", tag2.tag());
  }

  @Test
  public void testConstructor() {
    CategoryWordTag tag = new CategoryWordTag("A", "B", "C");
    assertEquals("Constructor category", "A", tag.category());
    assertEquals("Constructor tag", "B", tag.word());
    assertEquals("Constructor word", "C", tag.tag());
  }
}
