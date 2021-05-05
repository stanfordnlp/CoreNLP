package edu.stanford.nlp.util;

import java.util.HashSet;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the ArraySet class by running it through some standard set operations.
 *
 * @author John Bauer
 */
public class ArraySetTest {

  ArraySet<Integer> set;

  /**
   * Creates a small set of 3 elements.
   */
  @Before
  public void setUp() {
    set = new ArraySet<>();
    set.add(5);
    set.add(10);
    set.add(8);
  }

  @SuppressWarnings({"SimplifiableAssertion", "EqualsWithItself", "CollectionAddAllCanBeReplacedWithConstructor"})
  @Test
  public void testEquals() {
    Assert.assertTrue(set.equals(set));
    
    HashSet<Integer> hset = new HashSet<>();
    hset.addAll(set);

    Assert.assertTrue(set.equals(hset));
    Assert.assertTrue(hset.equals(set));
  }

  /**
   * Tests the set add function.
   * Note that add is probably already tested by the combination of
   * setUp and testEquals.
   */
  @Test
  public void testAdd() {
    Assert.assertTrue(set.contains(5));
    Assert.assertFalse(set.contains(4));

    for (int i = 0; i < 11; ++i) {
      set.add(i);
    }
    // 0..10, existing elements should not be readded
    Assert.assertEquals(11, set.size());

    Assert.assertTrue(set.contains(5));
    Assert.assertTrue(set.contains(4));
  }

  @Test
  public void testRemove() {
    Assert.assertFalse(set.contains(2));
    Assert.assertTrue(set.contains(5));
    set.remove(5);
    Assert.assertEquals(2, set.size());
    Assert.assertFalse(set.contains(2));
    Assert.assertFalse(set.contains(5));
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void testClear() {
    Assert.assertFalse(set.isEmpty());
    Assert.assertEquals(3, set.size());
    set.clear();
    Assert.assertTrue(set.isEmpty());
    Assert.assertEquals(0, set.size());
  }

}
