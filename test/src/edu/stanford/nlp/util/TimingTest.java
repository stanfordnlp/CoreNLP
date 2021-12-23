package edu.stanford.nlp.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import junit.framework.TestCase;

/** It seems like because of the way junit parallelizes tests that you just can't
 *  test timing to any degree of accuracy. So just try to make sure we're not
 *  off by an order of magnitude.
 *
 *  @author Christopher Manning
 */
public class TimingTest extends TestCase {

  @Override
  protected void setUp() {
    Locale.setDefault(Locale.US);
  }

  /** There's a lot of time slop in these tests so they don't fire by mistake.
   *  You definitely get them more than 50% off sometimes. :(
   *  And then we got a test failure that was over 70% off on the first test. :(
   *  So, really this only tests that the answers are right to an order of magnitude.
   */
  public void testTiming() {
    Timing t = new Timing();
    sleepTen();
    long val2 = t.reportNano();
    assertTrue(String.format("Wrong nanosleep %d", val2), val2 < 30_000_000);
    assertTrue(String.format("Wrong nanosleep %d", val2), val2 > 3_000_000);
    sleepTen();
    long val = t.report();
    // System.err.println(val);
    assertEquals("Wrong sleep", 25, val, 25);
    // On Linux, 6 loops is ~80ms which gets rounded up to 100 by the DecimalFormat.
    // On Windows, 6 loops is ~130ms which gets rounded down to 100
    for (int i = 0; i < 6; i++) {
      sleepTen();
    }
    long val3 = t.report();
    String timingString = Timing.toSecondsString(val3);
    String exp1 = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(0.1);
    String exp2 = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(0.2);
    assertTrue("Wrong formatted time", exp1.equals(timingString) || exp2.equals(timingString));
  }

  private static void sleepTen() {
    try {
      Thread.sleep(10);
    } catch (InterruptedException ie) {
      // do nothing
    }
  }

}
