package edu.stanford.nlp.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;
import junit.framework.TestCase;

public class CollectionValuedMapTest extends TestCase {

  /**
   * Tests add(), isEmpty(), get(), keySet(), values(), allValues(), entrySet(),
   * containsKey(), remove(), and clear()
   */
  public void testBasicOperations() {
    CollectionValuedMap<String, Integer> cvm = new CollectionValuedMap<String, Integer>();

    Assert.assertTrue(cvm.isEmpty());

    cvm.add("key1", 1);
    cvm.add("key1", 2);
    cvm.add("key1", 3);

    cvm.add("key2", 4);

    cvm.add("key3", 7);

    Assert.assertEquals(cvm.get("key1").size(), 3);
    Assert.assertEquals(cvm.get("key2").size(), 1);
    Assert.assertEquals(cvm.get("keyX").size(), 0);

    Assert.assertEquals(cvm.keySet().size(), 3);
    Assert.assertEquals(cvm.values().size(), 3);

    Assert.assertEquals(cvm.size(), 3);
    Assert.assertEquals(cvm.entrySet().size(), 3);
    Collection<Integer> allValues = cvm.allValues();
    Assert.assertEquals(allValues.size(), 5);

    Assert.assertTrue(cvm.containsKey("key1"));
    Assert.assertTrue(cvm.containsKey("key2"));
    Assert.assertTrue(cvm.containsKey("key3"));
    Assert.assertFalse(cvm.containsKey("keyX"));

    Assert.assertTrue(allValues.contains(1));
    Assert.assertTrue(allValues.contains(2));
    Assert.assertTrue(allValues.contains(3));
    Assert.assertTrue(allValues.contains(4));
    Assert.assertFalse(allValues.contains(5));

    Assert.assertFalse(cvm.isEmpty());

    cvm.remove("key3");

    Assert.assertTrue(cvm.containsKey("key1"));
    Assert.assertTrue(cvm.containsKey("key2"));
    Assert.assertFalse(cvm.containsKey("key3"));
    Assert.assertFalse(cvm.containsKey("keyX"));

    Assert.assertEquals(cvm.size(), 2);
    Assert.assertEquals(cvm.entrySet().size(), 2);
    Assert.assertEquals(cvm.allValues().size(), 4);

    Assert.assertEquals(cvm.keySet().size(), 2);
    Assert.assertEquals(cvm.values().size(), 2);

    cvm.remove("keyX"); // removing a non-existing key

    Assert.assertTrue(cvm.containsKey("key1"));
    Assert.assertTrue(cvm.containsKey("key2"));
    Assert.assertFalse(cvm.containsKey("key3"));
    Assert.assertFalse(cvm.containsKey("keyX"));

    Assert.assertEquals(cvm.size(), 2);
    Assert.assertEquals(cvm.entrySet().size(), 2);
    Assert.assertEquals(cvm.allValues().size(), 4);

    Assert.assertEquals(cvm.keySet().size(), 2);
    Assert.assertEquals(cvm.values().size(), 2);

    cvm.add("key4", 3);

    cvm.removeAll(Arrays.asList("key1", "key4"));

    Assert.assertFalse(cvm.containsKey("key1"));
    Assert.assertTrue(cvm.containsKey("key2"));
    Assert.assertFalse(cvm.containsKey("key3"));
    Assert.assertFalse(cvm.containsKey("key4"));
    Assert.assertFalse(cvm.containsKey("keyX"));

    
    cvm.clear();

    Assert.assertFalse(cvm.containsKey("key1"));
    Assert.assertFalse(cvm.containsKey("key2"));
    Assert.assertFalse(cvm.containsKey("key3"));
    Assert.assertFalse(cvm.containsKey("keyX"));

    Assert.assertEquals(cvm.size(), 0);
    Assert.assertEquals(cvm.allValues().size(), 0);
    Assert.assertEquals(cvm.entrySet().size(), 0);

    Assert.assertEquals(cvm.keySet().size(), 0);
    Assert.assertEquals(cvm.values().size(), 0);
  }

  /**
   * Tests various forms of addAll()/constructors, clone(), and equality
   */
  public void testMergingOperations() {
    CollectionValuedMap<String, Integer> cvm = new CollectionValuedMap<String, Integer>();
    cvm.add("key1", 1);
    cvm.add("key2", 2);
    cvm.add("key3", 3);

    Map<String, Integer> map = new HashMap<String, Integer>();
    map.put("key1", 1);
    map.put("key2", 2);
    map.put("key3", 3);

    CollectionValuedMap<String, Integer> cvmFromMap = new CollectionValuedMap<String, Integer>();
    cvmFromMap.addAll(map);

    Assert.assertEquals(cvm, cvmFromMap);

    CollectionValuedMap<String, Integer> cvmFromCvm = new CollectionValuedMap<String, Integer>(cvm);

    Assert.assertEquals(cvm, cvmFromCvm);

    CollectionValuedMap<String, Integer> cvmFromClone = cvm.clone();

    Assert.assertEquals(cvm, cvmFromClone);

    CollectionValuedMap<String, Integer> cvmToMerge = new CollectionValuedMap<String, Integer>();
    cvmToMerge.add("key1", 11);
    cvmToMerge.add("key5", 55);

    Assert.assertFalse(cvmToMerge.equals(cvm));

    cvm.addAll(cvmToMerge);

    CollectionValuedMap<String, Integer> expectedMerge = new CollectionValuedMap<String, Integer>();
    expectedMerge.add("key1", 1);
    expectedMerge.add("key1", 11);
    expectedMerge.add("key2", 2);
    expectedMerge.add("key3", 3);
    expectedMerge.add("key5", 55);

    Assert.assertEquals(cvm, expectedMerge);
  }
}