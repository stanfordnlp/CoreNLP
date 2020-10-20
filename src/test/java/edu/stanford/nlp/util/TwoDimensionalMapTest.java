package edu.stanford.nlp.util;

import junit.framework.TestCase;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Tests the 2-D hash map in various ways
 *
 * @author John Bauer
 */
public class TwoDimensionalMapTest extends TestCase {
  public void testBasicOperations() {
    TwoDimensionalMap<String, String, String> map = new TwoDimensionalMap<>();
    assertEquals(0, map.size());
    assertTrue(map.isEmpty());

    map.put("A", "B", "C");
    assertEquals("C", map.get("A", "B"));
    assertEquals(1, map.size());
    assertFalse(map.isEmpty());
    assertTrue(map.contains("A", "B"));
    assertFalse(map.contains("A", "C"));
    assertFalse(map.contains("B", "F"));

    map.put("A", "B", "D");
    assertEquals("D", map.get("A", "B"));
    assertEquals(1, map.size());
    assertFalse(map.isEmpty());
    assertTrue(map.contains("A", "B"));
    assertFalse(map.contains("A", "C"));
    assertFalse(map.contains("B", "F"));

    map.put("A", "C", "E");
    assertEquals("D", map.get("A", "B"));
    assertEquals("E", map.get("A", "C"));
    assertEquals(2, map.size());
    assertFalse(map.isEmpty());
    assertTrue(map.contains("A", "B"));
    assertTrue(map.contains("A", "C"));
    assertFalse(map.contains("B", "F"));

    map.put("B", "F", "G");
    assertEquals("D", map.get("A", "B"));
    assertEquals("E", map.get("A", "C"));
    assertEquals("G", map.get("B", "F"));
    assertEquals(3, map.size());
    assertFalse(map.isEmpty());
    assertTrue(map.contains("A", "B"));
    assertTrue(map.contains("A", "C"));
    assertTrue(map.contains("B", "F"));

    map.clear();
    assertEquals(0, map.size());
    assertTrue(map.isEmpty());
  }

  /**
   * Test that basic operations on a TwoDimensionalMap iterator work.
   */
  public void testBasicIterator() {
    TwoDimensionalMap<String, String, String> map = new TwoDimensionalMap<>();
    Iterator<TwoDimensionalMap.Entry<String, String, String>> mapIterator = map.iterator();
    assertFalse(mapIterator.hasNext());

    map.put("A", "B", "C");
    mapIterator = map.iterator();
    assertTrue(mapIterator.hasNext());
    TwoDimensionalMap.Entry<String, String, String> entry = mapIterator.next();
    assertEquals("A", entry.getFirstKey());
    assertEquals("B", entry.getSecondKey());
    assertEquals("C", entry.getValue());
    assertFalse(mapIterator.hasNext());

    map.put("A", "E", "F");
    map.put("D", "E", "F");
    map.put("G", "H", "I");
    map.put("J", "K", "L");

    assertEquals(5, map.size());
    int count = 0;
    Set<String> firstKeys = new HashSet<>();
    Set<String> values = new HashSet<>();
    for (TwoDimensionalMap.Entry<String, String, String> e : map) {
      ++count;
      firstKeys.add(e.getFirstKey());
      values.add(e.getValue());
    }
    assertTrue(firstKeys.contains("A"));
    assertTrue(firstKeys.contains("D"));
    assertTrue(firstKeys.contains("G"));
    assertTrue(firstKeys.contains("J"));
    assertTrue(values.contains("C"));
    assertTrue(values.contains("F"));
    assertTrue(values.contains("I"));
    assertTrue(values.contains("L"));
    assertEquals(5, count);
    assertEquals(4, firstKeys.size());
    assertEquals(4, values.size());
  }

  /**
   * Tests that a different map factory is used when asked for.  An
   * identity map will store two of the same key if the objects
   * themselves are different.  We can test for that.
   */
  public void testMapFactory() {
    TwoDimensionalMap<String, String, String> map = new TwoDimensionalMap<>(MapFactory.<String, Map<String, String>>identityHashMapFactory(), MapFactory.<String, String>identityHashMapFactory());
    map.put(new String("A"), "B", "C");
    map.put(new String("A"), "B", "C");
    assertEquals(2, map.size());
  }

  /**
   * Now that we know the MapFactory constructor should work and the
   * iterator should work, we can really test both by using a TreeMap
   * and checking that the iterated elements are sorted
   */
  public void testTreeMapIterator() {
    TwoDimensionalMap<String, String, String> map = new TwoDimensionalMap<>(MapFactory.<String, Map<String, String>>treeMapFactory(), MapFactory.<String, String>treeMapFactory());
    map.put("A", "B", "C");
    map.put("Z", "Y", "X");
    map.put("Z", "B", "C");
    map.put("A", "Y", "X");
    map.put("D", "D", "D");
    map.put("D", "F", "E");
    map.put("K", "G", "B");
    map.put("G", "F", "E");
    map.put("D", "D", "E");  // sneaky overwritten entry
    assertEquals(8, map.size());

    Iterator<TwoDimensionalMap.Entry<String, String, String>> mapIterator = map.iterator();
    TwoDimensionalMap.Entry<String, String, String> entry = mapIterator.next();
    assertEquals("A", entry.getFirstKey());
    assertEquals("B", entry.getSecondKey());
    assertEquals("C", entry.getValue());

    entry = mapIterator.next();
    assertEquals("A", entry.getFirstKey());
    assertEquals("Y", entry.getSecondKey());
    assertEquals("X", entry.getValue());

    entry = mapIterator.next();
    assertEquals("D", entry.getFirstKey());
    assertEquals("D", entry.getSecondKey());
    assertEquals("E", entry.getValue());

    entry = mapIterator.next();
    assertEquals("D", entry.getFirstKey());
    assertEquals("F", entry.getSecondKey());
    assertEquals("E", entry.getValue());

    entry = mapIterator.next();
    assertEquals("G", entry.getFirstKey());
    assertEquals("F", entry.getSecondKey());
    assertEquals("E", entry.getValue());

    entry = mapIterator.next();
    assertEquals("K", entry.getFirstKey());
    assertEquals("G", entry.getSecondKey());
    assertEquals("B", entry.getValue());

    entry = mapIterator.next();
    assertEquals("Z", entry.getFirstKey());
    assertEquals("B", entry.getSecondKey());
    assertEquals("C", entry.getValue());

    entry = mapIterator.next();
    assertEquals("Z", entry.getFirstKey());
    assertEquals("Y", entry.getSecondKey());
    assertEquals("X", entry.getValue());

    assertFalse(mapIterator.hasNext());



    Iterator<String> valueIterator = map.valueIterator();
    assertTrue(valueIterator.hasNext());
    assertEquals("C", valueIterator.next());
    assertEquals("X", valueIterator.next());
    assertEquals("E", valueIterator.next());
    assertEquals("E", valueIterator.next());
    assertEquals("E", valueIterator.next());
    assertEquals("B", valueIterator.next());
    assertEquals("C", valueIterator.next());
    assertEquals("X", valueIterator.next());
    assertFalse(valueIterator.hasNext());
  }

  /**
   * Tests that addAll works.  Also includes a sneaky equals() test
   */
  public void testAddAll() {
    TwoDimensionalMap<String, String, String> m1 = TwoDimensionalMap.hashMap();
    m1.put("A", "B", "1");
    m1.put("Z", "Y", "2");
    m1.put("Z", "B", "3");
    m1.put("A", "Y", "4");
    m1.put("D", "D", "5");
    m1.put("D", "F", "6");
    m1.put("K", "G", "7");
    m1.put("G", "F", "8");

    TwoDimensionalMap<String, String, String> m2 = TwoDimensionalMap.treeMap();
    m2.addAll(m1, Functions.<String>identityFunction());
    assertEquals(m1, m2);

    Function<String, Integer> valueOf = (String input)->{ return Integer.valueOf(input);};

    TwoDimensionalMap<String, String, Integer> m3 = TwoDimensionalMap.hashMap();
    m3.addAll(m1, valueOf);
    assertEquals(m1.size(), m3.size());
    assertEquals(3, m3.get("Z", "B").intValue());
  }
}

