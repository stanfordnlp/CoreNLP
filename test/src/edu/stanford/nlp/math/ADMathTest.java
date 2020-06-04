package edu.stanford.nlp.math;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ADMathTest {

  @Test
  public void testMult() {
    DoubleAD v1 = new DoubleAD(2.0, 4.0);
    DoubleAD v2 = new DoubleAD(3.0, 2.0);

    DoubleAD result = ADMath.mult(v1, v2);

    DoubleAD correctResult = new DoubleAD(6.0, 16.0);
    assertEquals(correctResult, result);
  }

  @Test
  public void testMultConst() {
    DoubleAD v1 = new DoubleAD(2.0, 4.0);
    Double constant = 4.0;

    DoubleAD result = ADMath.multConst(v1, constant);

    DoubleAD correctResult = new DoubleAD(8.0, 16.0);
    assertEquals(correctResult, result);
  }

  @Test
  public void testDivide() {
    DoubleAD v1 = new DoubleAD(4.0, 2.0);
    DoubleAD v2 = new DoubleAD(2.0, 0);

    DoubleAD result = ADMath.divide(v1, v2);

    DoubleAD correctResult = new DoubleAD(2.0, 1);
    assertEquals(correctResult, result);
  }

  @Test
  public void testDivideConst() {
    DoubleAD v1 = new DoubleAD(7.0, 3.0);
    Double constant = 2.0;

    DoubleAD result = ADMath.divideConst(v1, constant);

    DoubleAD correctResult = new DoubleAD(3.5, 1.5);
    assertEquals(correctResult, result);
  }

  @Test
  public void testExp() {
    DoubleAD v1 = new DoubleAD(3, 4);

    DoubleAD result = ADMath.exp(v1);

    DoubleAD correctResult = new DoubleAD(20.085536923187668, 80.34214769275067);
    assertEquals(correctResult, result);
  }

  @Test
  public void testLog() {
    DoubleAD v1 = new DoubleAD(5, 3);

    DoubleAD result = ADMath.log(v1);

    DoubleAD correctResult = new DoubleAD(1.6094379124341003, 0.6);
    assertEquals(correctResult, result);
  }

  @Test
  public void testPlus() {
    DoubleAD v1 = new DoubleAD(4.0, 2.0);
    DoubleAD v2 = new DoubleAD(2.0, 4.0);

    DoubleAD result = ADMath.plus(v1, v2);

    DoubleAD correctResult = new DoubleAD(6.0, 6.0);
    assertEquals(correctResult, result);
  }

  @Test
  public void testPlusConst() {
    DoubleAD v1 = new DoubleAD(7.0, 3.0);
    Double constant = 2.0;

    DoubleAD result = ADMath.plusConst(v1, constant);

    DoubleAD correctResult = new DoubleAD(9.0, 3.0);
    assertEquals(correctResult, result);
  }

  @Test
  public void testMinus() {
    DoubleAD v1 = new DoubleAD(4.0, 8.0);
    DoubleAD v2 = new DoubleAD(2.0, 4.0);

    DoubleAD result = ADMath.minus(v1, v2);

    DoubleAD correctResult = new DoubleAD(2.0, 4.0);
    assertEquals(correctResult, result);
  }

  @Test
  public void testMinusConst() {
    DoubleAD v1 = new DoubleAD(7.0, 3.0);
    Double constant = 2.0;

    DoubleAD result = ADMath.minusConst(v1, constant);

    DoubleAD correctResult = new DoubleAD(5.0, 3.0);
    assertEquals(correctResult, result);
  }
}