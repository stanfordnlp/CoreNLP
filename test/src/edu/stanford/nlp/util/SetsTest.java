package edu.stanford.nlp.util;

import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Sets;
import junit.framework.TestCase;
import java.util.*;

public class SetsTest extends TestCase {

  private Set<String> s1;
  private Set<String> s2;

  @Override
  public void setUp() {
    s1 = new HashSet<String>();
    s1.add("apple");
    s1.add("banana");
    s1.add("cherry");
    s1.add("dingleberry");
    s2 = new HashSet<String>();
    s2.add("apple");
    s2.add("banana");
    s2.add("cranberry");
  }

  public void testCross() {
    Set<Pair<String,String>> cross = Sets.cross(s1, s2);
    assertEquals(cross.size(), 12);
    Pair<String,String> p = new Pair<String,String>("dingleberry", "cranberry");
    assertTrue(cross.contains(p));
  }

  public void testDiff() {
    Set<String> diff = Sets.diff(s1, s2);
    assertEquals(diff.size(), 2);
    assertTrue(diff.contains("cherry"));
    assertFalse(diff.contains("apple"));
  }

  public void testUnion() {
    Set<String> union = Sets.union(s1, s2);
    assertEquals(union.size(), 5);
    assertTrue(union.contains("cherry"));
    assertFalse(union.contains("fungus"));
  }

  public void testIntersection() {
    Set<String> intersection = Sets.intersection(s1, s2);
    assertEquals(intersection.size(), 2);
    assertTrue(intersection.contains("apple"));
    assertFalse(intersection.contains("cherry"));
  }

  public void testPowerset() {
    Set<Set<String>> pow = Sets.powerSet(s1);
    assertEquals(pow.size(), 16);
  }

}
