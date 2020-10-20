package edu.stanford.nlp.stats;

import junit.framework.TestCase;

public class TwoDimensionalCounterTest extends TestCase {

  private TwoDimensionalCounter<String,String> c;

  @Override
  protected void setUp() {
    c = new TwoDimensionalCounter<String,String>();
    c.setCount("a","a", 1.0);
    c.setCount("a","b", 2.0);
    c.setCount("a","c", 3.0);
    c.setCount("b","a", 4.0);
    c.setCount("b","b", 5.0);
    c.setCount("c","a", 6.0);
  }

  public void testTotalCount() {
    assertEquals(c.totalCount(), 21.0);
  }
  
  public void testSetCount() {
    assertEquals(c.totalCount(), 21.0);
    c.setCount("p", "q", 1.0);
    assertEquals(c.totalCount(), 22.0);
    assertEquals(c.totalCount("p"), 1.0);
    assertEquals(c.getCount("p", "q"), 1.0);
    c.remove("p", "q");
  }

  public void testIncrement() {
    assertEquals(c.totalCount(), 21.0);
    assertEquals(c.getCount("b", "b"), 5.0);
    assertEquals(c.totalCount("b"), 9.0);
    c.incrementCount("b", "b", 2.0);
    assertEquals(c.getCount("b", "b"), 7.0);
    assertEquals(c.totalCount("b"), 11.0);
    assertEquals(c.totalCount(), 23.0);
    c.incrementCount("b", "b", -2.0);
    assertEquals(c.getCount("b", "b"), 5.0);
    assertEquals(c.totalCount("b"), 9.0);
    assertEquals(c.totalCount(), 21.0);
  }


}
