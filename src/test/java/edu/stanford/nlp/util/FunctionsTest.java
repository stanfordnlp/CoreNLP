package edu.stanford.nlp.util;

import junit.framework.TestCase;

import java.util.function.Function;


/**
 * Tests Functions utility class
 * 
 * @author dramage
 */
public class FunctionsTest extends TestCase {

  public void testCompose() {
    Function<Integer,Integer> plusOne = (Integer in)->{ return in + 1;};
    
    Function<Integer,Integer> doubler = (Integer in)->{ return in * 2;};
    
    Function<Integer,Integer> composed = Functions.compose(plusOne, doubler);
    assertEquals(composed.apply(1).intValue(), 3);
    assertEquals(composed.apply(2).intValue(), 5);
  }
  
}
