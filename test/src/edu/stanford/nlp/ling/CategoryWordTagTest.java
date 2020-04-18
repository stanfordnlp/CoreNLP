package edu.stanford.nlp.ling;

import junit.framework.TestCase;
import org.junit.Test;

public class CategoryWordTagTest extends TestCase {
  public void testCategoryWordTagConstructorWithExistingLabel() {
    CategoryWordTag tag = new CategoryWordTag("A", "B", "C");

    CategoryWordTag tag2 = new CategoryWordTag(tag);

    assertEquals("A", tag2.category());
    assertEquals("B", tag2.word());
    assertEquals("C", tag2.tag());
  }

  @Test
  public void testCategoryWordTagConstructorWithParCategoryWordTag() {
    CategoryWordTag tag = new CategoryWordTag("A", "B", "C");

    assertEquals("A", tag.category());
    assertEquals("B", tag.word());
    assertEquals("C", tag.tag());
  }
}
