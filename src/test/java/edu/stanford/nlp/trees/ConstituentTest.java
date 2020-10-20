package edu.stanford.nlp.trees;

import java.util.*;
import edu.stanford.nlp.ling.StringLabel;
import junit.framework.TestCase;

/**
 * ConstituentTest.java
 *
 * @author Christopher Manning
 * @author Sebastian Pado
 */

public class ConstituentTest extends TestCase {

  public void testConstituents() {
    Set<Constituent> set = new HashSet<Constituent>();
    Constituent c1 = new LabeledScoredConstituent(9,15,new StringLabel("S"),0);
    Constituent c2 = new LabeledScoredConstituent(9,15,new StringLabel("VP"),0);
  //  System.err.println("c1 "+c1+" c2 "+c2+" equal? "+c1.equals(c2));
    assertNotSame(c1,c2);
    set.add(c1);
  //  System.err.println("Set has c1? "+set.contains(c1));
   // System.err.println("Set has c2? "+set.contains(c2));
    assertTrue(set.contains(c1));
    assertFalse(set.contains(c2));
    set.add(c2);
  //  System.err.println("Set has c1? "+set.contains(c1));
  //  System.err.println("Set has c2? "+set.contains(c2));
    assertTrue(set.contains(c1));
    assertTrue(set.contains(c2));
 //   System.err.println("Set size is " + set.size());
    assertTrue(set.size() == 2);
    for (Constituent c : set) {
   //   System.err.println(" "+c+" is c1? "+c.equals(c1)+" or "+c1.equals(c)+" is c2? "+c.equals(c2)+" or "+c2.equals(c));
      assertTrue((c.equals(c1) || c.equals(c2)));
    }
    // there used to be a parallel test for Constituents in TreeSets,
    // but given that Constituents do not implement Comparable(),
    // this test just always failed.
  }
}
