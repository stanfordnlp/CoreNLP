package edu.stanford.nlp.util;

import junit.framework.TestCase;

import java.util.HashSet;

/**
 * Tests the ArraySet class by running it through some standard
 * set operations.
 *
 * @author John Bauer
 */
public class ArraySetTest extends TestCase {
  ArraySet<Integer> set;
  /**
   * Creates a small set of 3 elements.
   */
  public void setUp() {
    set = new ArraySet<Integer>();
    set.add(5);
    set.add(10);
    set.add(8);
  }

  public void testEquals() {
    assertTrue(set.equals(set));
    
    HashSet<Integer> hset = new HashSet<>();
    hset.addAll(set);

    assertTrue(set.equals(hset));
    assertTrue(hset.equals(set));
  }

  /**
   * Tests the set add function.
   * Note that add is probably already tested by the combination of
   * setUp and testEquals.
   */
  public void testAdd() {
    assertTrue(set.contains(5));
    assertFalse(set.contains(4));

    for (int i = 0; i < 11; ++i) {
      set.add(i);
    }
    // 0..10, existing elements should not be readded
    assertEquals(11, set.size());

    assertTrue(set.contains(5));
    assertTrue(set.contains(4));
  }

  public void testRemove() {
    assertFalse(set.contains(2));
    assertTrue(set.contains(5));
    set.remove(5);
    assertEquals(2, set.size());
    assertFalse(set.contains(2));
    assertFalse(set.contains(5));
  }

  public void testClear() {
    assertFalse(set.isEmpty());
    assertEquals(3, set.size());
    set.clear();
    assertTrue(set.isEmpty());
    assertEquals(0, set.size());
  }
}