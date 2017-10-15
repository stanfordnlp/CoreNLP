package edu.stanford.nlp.optimization;

import java.util.function.Function;
import junit.framework.TestCase;

public class GoldenSectionLineSearchTest extends TestCase {
    public void testEasy() {
      GoldenSectionLineSearch min = new GoldenSectionLineSearch(false, 0.00001, 0.0, 1.0, false);
      Function<Double,Double> f2 = (Double x)->{ return x < 0.1 ? 0.0: (x > 0.2 ? 0.0: (x - 0.1) * (x - 0.2));};
      assertEquals(0.15,min.minimize(f2),1E-4);
    }
}
