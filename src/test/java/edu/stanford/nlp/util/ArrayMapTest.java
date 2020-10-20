package edu.stanford.nlp.util;

import junit.framework.TestCase;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tests the ArrayMap class by running it through some standard
 * map operations.
 *
 * @author John Bauer
 */
public class ArrayMapTest extends TestCase {
  ArrayMap<String, Integer> map;
  HashMap<String, Integer> hmap;

  public void setUp() {
    map = new ArrayMap<String, Integer>();
    hmap = new HashMap<String, Integer>();

    map.put("Foo", 5);
    map.put("Bar", 50);
    map.put("Baz", 500);

    hmap.put("Foo", 5);
    hmap.put("Bar", 50);
    hmap.put("Baz", 500);
  }

  public void testArrayMapEqualsHashMap() {
    assertTrue(map.equals(map));
  }

  public void testHashMapEqualsArrayMap() {
    assertEquals(hmap, map);
  }

  public void testClear() {
    assertFalse(map.isEmpty());
    map.clear();
    assertTrue(map.isEmpty());
    map.put("aaa", 5);
    assertEquals(1, map.size());
    assertFalse(map.isEmpty());
  }

  public void testPutAll() {
    map.clear();
    assertTrue(map.isEmpty());
    map.putAll(hmap);
    testArrayMapEqualsHashMap();
    testHashMapEqualsArrayMap();
    HashMap<String, Integer> newmap = new HashMap<>();
    newmap.putAll(map);
    assertEquals(newmap, map);
    assertEquals(map, newmap);
  }

  public void testEntrySet() {
    Set<Map.Entry<String, Integer>> entries = map.entrySet();
    Map.Entry<String, Integer> entry = entries.iterator().next();
    entries.remove(entry);
    assertFalse(map.containsKey(entry.getKey()));
    assertEquals(2, map.size());
    entries.clear();
    assertEquals(0, map.size());
    assertTrue(map.isEmpty());
  }

  public void testValues() {
    Set<Integer> hmapValues = new HashSet<>();
    hmapValues.addAll(hmap.values());

    Set<Integer> mapValues = new HashSet<>();
    mapValues.addAll(map.values());

    assertEquals(hmapValues, mapValues);
  }

  public void testPutDuplicateValues() {
    map.clear();
    map.put("Foo", 6);
    assertEquals(6, map.get("Foo").intValue());
    assertEquals(1, map.size());
    map.put("Foo", 5);
    assertEquals(5, map.get("Foo").intValue());
    assertEquals(1, map.size());
  }
}
