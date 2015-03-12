package edu.stanford.nlp.util;

import junit.framework.TestCase;

/**
 * @author Christopher Manning
 */
public class TimingTest extends TestCase {

  /** There's a lot of time slop in these tests so they don't fire by mistake.
   *  You definitely get them more than 10% off sometimes. :(
   */
  public void testTiming() {
    Timing t = new Timing();
    sleepTen();
    long val2 = t.reportNano();
    assertEquals("Wrong nanosleep", 10000000, val2, 2000000);
    // System.err.println(val2);
    sleepTen();
    long val = t.report();
    // System.err.println(val);
    assertEquals("Wrong sleep", 20, val, 4);
    for (int i = 0; i < 8; i++) {
      sleepTen();
    }
    long val3 = t.report();
    assertEquals("Wrong formatted time", "0.1", Timing.toSecondsString(val3));
  }

  private static void sleepTen() {
    try {
      Thread.sleep(10);
    } catch (InterruptedException ie) {
      // do nothing
    }
  }

}