package edu.stanford.nlp.stats;

import junit.framework.TestCase;

/**
 * @author Christopher Manning
 */
public class TwoDimensionalIntCounterTest extends TestCase {

  public void testTraditionalMain() {
    TwoDimensionalIntCounter<String,String> cc = new TwoDimensionalIntCounter<>();
    cc.setCount("a", "c", 1.0);
    cc.setCount("b", "c", 1.0);
    cc.setCount("a", "d", 1.0);
    cc.setCount("a", "d", -1.0);
    cc.setCount("b", "d", 1.0);
    assertEquals("Error in counter setup", 1.0, cc.getCount("a", "c"), 1e-8);
    assertEquals("Error in counter setup", 1.0, cc.getCount("b", "c"), 1e-8);
    assertEquals("Error in counter setup", -1.0, cc.getCount("a", "d"), 1e-8);
    assertEquals("Error in counter setup", 1.0, cc.getCount("b", "d"), 1e-8);
    assertEquals("Error in counter setup", 0.0, cc.getCount("a", "a"), 1e-8);

    cc.incrementCount("b", "d", 1.0);
    assertEquals("Error in counter increment", -1.0, cc.getCount("a", "d"), 1e-8);
    assertEquals("Error in counter increment", 2.0, cc.getCount("b", "d"), 1e-8);
    assertEquals("Error in counter increment", 0.0, cc.getCount("a", "a"), 1e-8);

    TwoDimensionalIntCounter<String,String> cc2 = TwoDimensionalIntCounter.reverseIndexOrder(cc);
    assertEquals("Error in counter reverseIndexOrder", 1.0, cc2.getCount("c", "a"), 1e-8);
    assertEquals("Error in counter reverseIndexOrder", 1.0, cc2.getCount("c", "b"), 1e-8);
    assertEquals("Error in counter reverseIndexOrder", -1.0, cc2.getCount("d", "a"), 1e-8);
    assertEquals("Error in counter reverseIndexOrder", 2.0, cc2.getCount("d", "b"), 1e-8);
    assertEquals("Error in counter reverseIndexOrder", 0.0, cc2.getCount("a", "a"), 1e-8);
    assertEquals("Error in counter reverseIndexOrder", 0.0, cc2.getCount("a", "c"), 1e-8);
  }

}