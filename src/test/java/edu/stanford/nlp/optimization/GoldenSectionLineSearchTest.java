package edu.stanford.nlp.optimization;

import java.util.function.DoubleUnaryOperator;

import org.junit.Assert;
import org.junit.Test;

public class GoldenSectionLineSearchTest {

  @Test
  public void testEasy() {
    GoldenSectionLineSearch min = new GoldenSectionLineSearch(false, 0.00001, 0.0, 1.0, false);
    DoubleUnaryOperator f2 = x -> {
      // this function used to fail in Galen's version; min should be 0.2
      // return - x * (2 * x - 1) * (x - 0.8);
      // this function fails if you don't find an initial bracketing
      return x < 0.1 ? 0.0: (x > 0.2 ? 0.0: (x - 0.1) * (x - 0.2));
      // return - Math.sin(x * Math.PI);
      // return -(3 + 6 * x - 4 * x * x);
    };
    Assert.assertEquals(0.15, min.minimize(f2), 1E-4);
  }

}
