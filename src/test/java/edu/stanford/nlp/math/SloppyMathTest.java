package edu.stanford.nlp.math;

import edu.stanford.nlp.util.Triple;
import junit.framework.TestCase;

public class SloppyMathTest extends TestCase {

  @Override
  public void setUp() {
  }

  public void testRoundWithBoundaries() {
    assertEquals("Boundary off point positive", 0.0, SloppyMath.round(0.499));
    assertEquals("Boundary on point", 1.0, SloppyMath.round(0.5));
    assertEquals("Boundary on point negative", 0.0, SloppyMath.round(-0.5));
    assertEquals("Boundary off point negative", -1.0, SloppyMath.round(-0.51));
    assertEquals("Round 10 to 10", 10.0, SloppyMath.round(10));
    assertEquals("Round 10.32 to 10", 10.0, SloppyMath.round(10.32));
  }

  public void testRound2() {
    assertEquals(0.0, SloppyMath.round(49, -2));
    assertEquals(100.0, SloppyMath.round(50, -2));
    assertEquals(100.0, SloppyMath.round(51, -2));
    assertEquals(0.0, SloppyMath.round(-50, -2));
    assertEquals(-100.0, SloppyMath.round(-51, -2));
    assertEquals(3.14, SloppyMath.round(Math.PI, 2));
    assertEquals(400.0, SloppyMath.round(431.5, -2));
    assertEquals(432.0, SloppyMath.round(431.5, 0));
    assertEquals(0.0, SloppyMath.round(-0.05, 1));
    assertEquals(-0.05, SloppyMath.round(-0.05, 2));
  }

  public void testMax() {
    assertEquals(3, SloppyMath.max(1, 2, 3));
  }

  public void testMin() {
    assertEquals(1, SloppyMath.min(1, 2, 3));
  }

  public void testIsDangerous() {
    assertTrue(SloppyMath.isDangerous(Double.POSITIVE_INFINITY) &&
            SloppyMath.isDangerous(Double.NaN) &&
            SloppyMath.isDangerous(0));
  }

  public void testIsVeryDangerous() {
    assertTrue(SloppyMath.isDangerous(Double.POSITIVE_INFINITY) &&
            SloppyMath.isDangerous(Double.NaN));
  }

  public void testLogAdd() {
    double d1 = 0.1;
    double d2 = 0.2;
    double lsum = SloppyMath.logAdd(d1, d2);
    double myLsum = 0;
    myLsum += Math.exp(d1);
    myLsum += Math.exp(d2);
    myLsum = Math.log(myLsum);
    assertTrue(myLsum == lsum);
  }

  public void testIntPow() {
    assertTrue(SloppyMath.intPow(3, 5) == Math.pow(3, 5));
    assertTrue(SloppyMath.intPow(3.3, 5) - Math.pow(3.3, 5) < 1e-4);
    assertEquals(1, SloppyMath.intPow(5, 0));
    assertEquals(3125, SloppyMath.intPow(5, 5));
    assertEquals(32, SloppyMath.intPow(2, 5));
    assertEquals(3, SloppyMath.intPow(3, 1));
    assertEquals(1158.56201, SloppyMath.intPow(4.1, 5), 1e-4);
    assertEquals(1158.56201f, SloppyMath.intPow(4.1f, 5), 1e-2);
  }

  public void testArccos() {
    assertEquals(Math.PI, SloppyMath.acos(-1.0), 0.001);
    assertEquals(0, SloppyMath.acos(1.0), 0.001);
    assertEquals(Math.PI / 2, SloppyMath.acos(0.0), 0.001);
    for (double x = -1.0; x < 1.0; x += 0.001) {
      assertEquals(Math.acos(x), SloppyMath.acos(x), 0.001);
    }
    try {
      SloppyMath.acos(-1.0000001);
      assertFalse(true);
    } catch (IllegalArgumentException e) {
    }
    try {
      SloppyMath.acos(1.0000001);
      assertFalse(true);
    } catch (IllegalArgumentException e) {
    }
  }

  public void testPythonMod() {
    assertEquals(0, SloppyMath.pythonMod(9, 3));
    assertEquals(0, SloppyMath.pythonMod(-9, 3));
    assertEquals(0, SloppyMath.pythonMod(9, -3));
    assertEquals(0, SloppyMath.pythonMod(-9, -3));
    assertEquals(2, SloppyMath.pythonMod(8, 3));
    assertEquals(1, SloppyMath.pythonMod(-8, 3));
    assertEquals(-1, SloppyMath.pythonMod(8, -3));
    assertEquals(-2, SloppyMath.pythonMod(-8, -3));
  }

  public void testParseDouble() {
    for (int base = -10; base < 10; ++base) {
      if (base == 0) {
        continue;
      }
      for (int exponent = -100; exponent < 100; ++exponent) {
        double number = Math.pow(Math.PI * base, exponent);
        Triple<Boolean, Long, Integer> parts = SloppyMath.segmentDouble(number);
        double parsed = SloppyMath.parseDouble(parts.first, parts.second, parts.third);
        assertEquals(number, parsed, Math.abs(parsed) / 1.0e5);
      }
    }
  }

  public void testParseInt() {
    assertEquals(42, SloppyMath.parseInt("42"));
    assertEquals(-42, SloppyMath.parseInt("-42"));
    assertEquals(42000000000000l, SloppyMath.parseInt("42000000000000"));
  }

  public void testParseIntWithBoundaries() {
    assertEquals(Long.MAX_VALUE, SloppyMath.parseInt("9223372036854775807"));
    assertEquals(Long.MIN_VALUE, SloppyMath.parseInt("-9223372036854775808"));
    assertEquals(20, SloppyMath.parseInt("20"));
    //overflows
    assertEquals(Long.MAX_VALUE, SloppyMath.parseInt("-9223372036854775809"));
    assertEquals(Long.MIN_VALUE, SloppyMath.parseInt("9223372036854775808"));
  }
}

