package edu.stanford.nlp.patterns.surface;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

import edu.stanford.nlp.patterns.surface.SurfacePattern.Genre;

public class SurfacePatternTest {

  @Before
  public void setUp(){}
  
  @Test
  public void testSubsumesArray(){
    String[] arr1 = {",","line",",","on"};
    String[] arr2 = {",","line",","};
    assertTrue(SurfacePattern.subsumesArray(arr1, arr2));
    assertFalse(SurfacePattern.subsumesArray(arr2, null));
  }
  
  @Test
  public void testSimplerTokens(){
    String[] prevContext = {"[{lemma:/\\Q,\\E/}]", "[{word  :/\\Qhappy\\E/}]", "[{DT:DT}]"};
    String[] nextContext = null;
    PatternToken token = new PatternToken("V", false, true, 2, null, false, false, null);
    //SurfacePattern p = new SurfacePattern(prevContext, token, nextContext, Genre.PREV);
    //String[] sim = p.getSimplerTokensPrev();
    //System.out.println(Arrays.toString(sim));
  }
}
