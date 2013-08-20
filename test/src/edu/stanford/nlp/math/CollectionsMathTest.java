package edu.stanford.nlp.math;

import java.util.Arrays;
import java.util.List;

import junit.framework.Assert;
import junit.framework.TestCase;

public class CollectionsMathTest extends TestCase {
  public void testMinMax() {
    List<Double> values = Arrays.asList(2.5, 7.5, 5.0);
    Assert.assertEquals(2.5, CollectionsMath.min(values));
    Assert.assertEquals(7.5, CollectionsMath.max(values));
  }
  
  public void testMean() {
    List<Double> values = Arrays.asList(2.5, 7.5, 5.0);
    Assert.assertEquals(5.0, CollectionsMath.mean(values));
  }
  
  public void testVariance() {
    List<Double> values = Arrays.asList(2.5, 7.5, 5.0);
    Assert.assertEquals(6.25, CollectionsMath.variance(values));
  }

  public void testStdev() {
    List<Double> values = Arrays.asList(2.5, 7.5, 5.0);
    Assert.assertEquals(2.5, CollectionsMath.stdev(values));
  }

}
