package edu.stanford.nlp.patterns.surface;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.patterns.PatternFactory;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.concurrent.ConcurrentHashIndex;
import org.junit.Before;
import org.junit.Test;

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

  Token[] createContext(Map<Class, String> res){
    Token[] toks = new Token[res.size()];
    int i =0;
    for(Map.Entry<Class, String> en: res.entrySet()){
      Token t = new Token(PatternFactory.PatternType.SURFACE);

      t.addORRestriction(en.getKey(), en.getValue());
      toks[i] = t;
      i++;
    }
    return toks;
  }

  @Test
  public void testSimplerTokens(){
    Map<Class, String> prev = new HashMap<Class, String>(){{
      put(CoreAnnotations.LemmaAnnotation.class, "name");
      put(CoreAnnotations.LemmaAnnotation.class, "is");
    }};

    Map<Class, String> next = new HashMap<Class, String>(){{
      put(CoreAnnotations.LemmaAnnotation.class, "Duck");
    }};


    PatternToken token = new PatternToken("V", false, true, 2, null, false, false, null);

    SurfacePattern p = new SurfacePattern(createContext(prev), token, createContext(next), SurfacePatternFactory.Genre.PREVNEXT);

    Map<Class, String> prev2 = new HashMap<Class, String>(){{
      put(CoreAnnotations.LemmaAnnotation.class, "name");
      put(CoreAnnotations.LemmaAnnotation.class, "is");
    }};

    Map<Class, String> next2 = new HashMap<Class, String>(){{
      put(CoreAnnotations.LemmaAnnotation.class, "Duck");
    }};

    PatternToken token2 = new PatternToken("V", false, true, 2, null, false, false, null);

    SurfacePattern p2 = new SurfacePattern(createContext(prev2), token2, createContext(next2), SurfacePatternFactory.Genre.PREVNEXT);

    assert p.compareTo(p2) == 0;

    Counter<SurfacePattern> pats = new ClassicCounter<>();
    pats.setCount(p, 1);
    pats.setCount(p2, 1);

    assert pats.size() ==1;
    System.out.println("pats size is " + pats.size());

    ConcurrentHashIndex<SurfacePattern> index = new ConcurrentHashIndex<>();
    index.add(p);
    index.add(p2);
    assert index.size() ==1;

    //String[] sim = p.getSimplerTokensPrev();
    //System.out.println(Arrays.toString(sim));
  }
}
