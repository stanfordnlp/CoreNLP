package edu.stanford.nlp.ling;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CategoryWordTagTest {

  @Test
  public void testCategoryWordTagConstructorWithExistingLabel() {
    CategoryWordTag tag = new CategoryWordTag("A", "B", "C");

    CategoryWordTag tag2 = new CategoryWordTag(tag);

    assertEquals("Testing method category", "A", tag2.category());
    assertEquals("Testing method word", "B", tag2.word());
    assertEquals("Testing method tag", "C", tag2.tag());
  }

  @Test
  public void testCategoryWordTagConstructorWithParCategoryWordTag() {
    CategoryWordTag tag = new CategoryWordTag("A", "B", "C");

    assertEquals("Testing method category", "A", tag.category());
    assertEquals("Testing method word", "B", tag.word());
    assertEquals("Testing method tag", "C", tag.tag());
  }
}