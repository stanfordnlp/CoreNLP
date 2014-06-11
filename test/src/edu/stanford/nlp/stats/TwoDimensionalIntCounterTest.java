package edu.stanford.nlp.stats;

import junit.framework.TestCase;

/**
 * @author Christopher Manning
 */
public class TwoDimensionalIntCounterTest extends TestCase {

  public void testTraditionalMain() {
    String answer1 = "b\td\t1.0\n" +
            "b\tc\t1.0\n" +
            "a\td\t-1.0\n" +
            "a\tc\t1.0\n";
    String answer2 = "b\td\t2.0\n" +
            "b\tc\t1.0\n" +
            "a\td\t-1.0\n" +
            "a\tc\t1.0\n";
    String answer3 = "d\tb\t2.0\n" +
            "d\ta\t-1.0\n" +
            "c\tb\t1.0\n" +
            "c\ta\t1.0\n";

    TwoDimensionalIntCounter<String,String> cc = new TwoDimensionalIntCounter<String,String>();
    cc.setCount("a", "c", 1.0);
    cc.setCount("b", "c", 1.0);
    cc.setCount("a", "d", 1.0);
    cc.setCount("a", "d", -1.0);
    cc.setCount("b", "d", 1.0);
    assertEquals("Error in counter setup", answer1, cc.toString());
    cc.incrementCount("b", "d", 1.0);
    assertEquals("Error in counter setup", answer2, cc.toString());
    TwoDimensionalIntCounter<String,String> cc2 = TwoDimensionalIntCounter.reverseIndexOrder(cc);
    assertEquals("Error in counter setup", answer3, cc2.toString());
  }

}