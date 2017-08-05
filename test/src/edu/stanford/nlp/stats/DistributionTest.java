package edu.stanford.nlp.stats;

import junit.framework.TestCase;

/**
 * 
 * @author lmthang
 *
 */
public class DistributionTest extends TestCase {
  
  public void testGetDistributionFromLogValues(){
    Counter<String> c1 = new ClassicCounter<>();
    c1.setCount("p", 1.0);
    c1.setCount("q", 2.0);
    c1.setCount("r", 3.0);
    c1.setCount("s", 4.0);
    
    // take log
    Counters.logInPlace(c1);
    
    // now call distribution
    Distribution<String> distribution = Distribution.getDistributionFromLogValues(c1);
    
    // test
    assertEquals(distribution.keySet().size(), 4); // size
    
    // keys
    assertEquals(distribution.containsKey("p"), true);
    assertEquals(distribution.containsKey("q"), true);
    assertEquals(distribution.containsKey("r"), true);
    assertEquals(distribution.containsKey("s"), true);
    
    // values
    assertEquals(distribution.getCount("p"), 1.0E-1, 1E-10);
    assertEquals(distribution.getCount("q"), 2.0E-1, 1E-10);
    assertEquals(distribution.getCount("r"), 3.0E-1, 1E-10);
    assertEquals(distribution.getCount("s"), 4.0E-1, 1E-10);
  }
}
