package edu.stanford.nlp.stats;

import java.util.HashMap;

/**
 * Tests a counter that wraps an underlying map.
 * 
 * @author dramage
 */
public class WrappedMapCounterTest extends CounterTestBase {
  
  public WrappedMapCounterTest() {
    super(Counters.fromMap(new HashMap<String,Double>(),Double.class));
  }

}
