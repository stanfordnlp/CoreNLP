package edu.stanford.nlp.ling;

import junit.framework.TestCase;
import org.junit.Test;

public class CategoryWordTagFactoryTest extends TestCase {
  public void testCopy() {
    CategoryWordTag tag = new CategoryWordTag("A", "B", "C");
    CategoryWordTagFactory lf = new CategoryWordTagFactory();
    Label label = lf.newLabel(tag);
    assertTrue(label instanceof CategoryWordTag);
    CategoryWordTag tag2 = (CategoryWordTag) label;
    assertEquals("A", tag2.category());
    assertEquals("B", tag2.word());
    assertEquals("C", tag2.tag());
  }

  @Test
  public void testNewLabelMethodReturnsInstanceOfCategoryWordTag(){
      CategoryWordTag tag = new CategoryWordTag("A", "B", "C");
      CategoryWordTagFactory lf = new CategoryWordTagFactory();
      Label label = lf.newLabel(tag);
      assertTrue(label instanceof CategoryWordTag);
  }
}
