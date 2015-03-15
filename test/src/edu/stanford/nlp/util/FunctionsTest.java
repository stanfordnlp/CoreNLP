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
    Function<Integer,Integer> plusOne = new Function<Integer,Integer>() {
      public Integer apply(Integer in) {
        return in + 1;
      }
    };
    
    Function<Integer,Integer> doubler = new Function<Integer,Integer>() {
      public Integer apply(Integer in) {
        return in * 2;
      }
    };
    
    Function<Integer,Integer> composed = Functions.compose(plusOne, doubler);
    assertEquals(composed.apply(1).intValue(), 3);
    assertEquals(composed.apply(2).intValue(), 5);
  }
  
}
