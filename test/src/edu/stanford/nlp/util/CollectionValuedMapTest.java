package edu.stanford.nlp.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;



public class CollectionValuedMapTest {

  /**
   * Tests add(), isEmpty(), get(), keySet(), values(), allValues(), entrySet(),
   * containsKey(), remove(), and clear().
   */
  @Test
  public void testBasicOperations() {
    CollectionValuedMap<String, Integer> cvm = new CollectionValuedMap<>();

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

  @Test
  public void testRemoveMapping() {
    CollectionValuedMap<String, Integer> cvm = new CollectionValuedMap<>();
    cvm.add("key1", 1);
    cvm.add("key2", 1);
    cvm.add("key2", 2);
    cvm.add("key3", 3);
    Assert.assertEquals(3, cvm.size());

    Assert.assertEquals(1, cvm.get("key3").size());
    cvm.removeMapping("key3", 3);
    Assert.assertEquals(0, cvm.get("key3").size());
    cvm.removeMapping("key3", 3);
    Assert.assertEquals(0, cvm.get("key3").size());

    Assert.assertEquals(2, cvm.get("key2").size());
    cvm.removeMapping("key2", 2);
    Assert.assertEquals(1, cvm.get("key2").size());
    cvm.removeMapping("key2", 2);
    Assert.assertEquals(1, cvm.get("key2").size());
    cvm.removeMapping("key2", 1);
    Assert.assertEquals(0, cvm.get("key2").size());
  }

  /**
   * Tests various forms of addAll()/constructors, clone(), and equality.
   */
  @Test
  public void testMergingOperations() {
    CollectionValuedMap<String, Integer> cvm = new CollectionValuedMap<>();
    cvm.add("key1", 1);
    cvm.add("key2", 2);
    cvm.add("key3", 3);

    Map<String, Integer> map = new HashMap<>();
    map.put("key1", 1);
    map.put("key2", 2);
    map.put("key3", 3);

    CollectionValuedMap<String, Integer> cvmFromMap = new CollectionValuedMap<>();
    cvmFromMap.addAll(map);

    Assert.assertEquals(cvm, cvmFromMap);

    CollectionValuedMap<String, Integer> cvmFromCvm = new CollectionValuedMap<>(cvm);

    Assert.assertEquals(cvm, cvmFromCvm);

    // CollectionValuedMap<String, Integer> cvmFromClone = cvm.clone();
    // Assert.assertEquals(cvm, cvmFromClone);

    CollectionValuedMap<String, Integer> cvmToMerge = new CollectionValuedMap<>();
    cvmToMerge.add("key1", 11);
    cvmToMerge.add("key5", 55);

    Assert.assertNotEquals(cvmToMerge, cvm);

    cvm.addAll(cvmToMerge);

    CollectionValuedMap<String, Integer> expectedMerge = new CollectionValuedMap<>();
    expectedMerge.add("key1", 1);
    expectedMerge.add("key1", 11);
    expectedMerge.add("key2", 2);
    expectedMerge.add("key3", 3);
    expectedMerge.add("key5", 55);

    Assert.assertEquals(cvm, expectedMerge);
  }

  /**
   * Tests add/remove (again).
   */
  @Test
  public void testAddRemove() {
    CollectionValuedMap<Integer, Integer> fooMap = new CollectionValuedMap<>();
    CollectionValuedMap<Integer, Integer> expectedMap = new CollectionValuedMap<>();
    for (int i = 0; i < 4; i++) {
      for (int j = 0; j < 4; j++) {
        fooMap.add(Integer.valueOf(i), Integer.valueOf(j));
        if (i!=2){
          expectedMap.add(Integer.valueOf(i), Integer.valueOf(j));
        }
      }
    }
    fooMap.remove(Integer.valueOf(2));
    Assert.assertEquals(expectedMap,fooMap);
  }

  /**
   * Tests add/remove (again).
   */
  @Test
  public void testRandomAddRemoveAndDelta() {
    CollectionValuedMap<Integer, Integer> originalMap = new CollectionValuedMap<>();
    Random r = new Random();
    for (int i = 0; i < 800; i++) {
      Integer rInt1 = Integer.valueOf(r.nextInt(400));
      Integer rInt2 = Integer.valueOf(r.nextInt(400));
      originalMap.add(rInt1, rInt2);
      // System.out.println("Adding " + rInt1 + ' ' + rInt2);
    }
    CollectionValuedMap<Integer, Integer> originalCopyMap = new CollectionValuedMap<>(originalMap);
    CollectionValuedMap<Integer, Integer> deltaCopyMap = new CollectionValuedMap<>(originalMap);
    CollectionValuedMap<Integer, Integer> deltaMap = new DeltaCollectionValuedMap<>(originalMap);
    CollectionValuedMap<Integer, Integer> delta2Map = originalMap.deltaCopy();
    // now make a lot of changes to deltaMap;
    // add and change some stuff
    for (int i = 0; i < 400; i++) {
      Integer rInt1 = Integer.valueOf(r.nextInt(400));
      Integer rInt2 = Integer.valueOf(r.nextInt(400) + 1000);
      deltaMap.add(rInt1, rInt2);
      delta2Map.add(rInt1, rInt2);
      deltaCopyMap.add(rInt1, rInt2);
      // System.out.println("Adding " + rInt1 + ' ' + rInt2);
    }
    // remove some stuff
    for (int i = 0; i < 400; i++) {
      Integer rInt1 = Integer.valueOf(r.nextInt(1400));
      Integer rInt2 = Integer.valueOf(r.nextInt(1400));
      deltaMap.removeMapping(rInt1, rInt2);
      delta2Map.removeMapping(rInt1, rInt2);
      deltaCopyMap.removeMapping(rInt1, rInt2);
      // System.out.println("Removing " + rInt1 + ' ' + rInt2);
    }
    // System.out.println("original: " + originalMap);
    // System.out.println("orig cop: " + originalCopyMap);
    // System.out.println("dcopy: " + deltaCopyMap);
    // System.out.println("delta: " + deltaMap);

    Assert.assertEquals("Copy map not identical", originalMap, originalCopyMap);
    Assert.assertEquals("Delta map not equal to copy", deltaCopyMap, deltaMap);
    Assert.assertEquals("Delta2Map not equal to copy", deltaCopyMap, delta2Map);
  }

}
