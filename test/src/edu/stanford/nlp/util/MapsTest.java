package edu.stanford.nlp.util;

import junit.framework.TestCase;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Test some (well, just one at the moment) of the utility methods in Maps
 *
 * @author John Bauer
 */
public class MapsTest extends TestCase {
  public void testAddAllWithFunction() {
    Map<String, String> stringMap = new HashMap<>();
    Map<String, Integer> intMap = new HashMap<>();

    Function<Integer, String> toString = new Function<Integer, String>() {
      public String apply(Integer i) {
        return i.toString();
      }
    };

    Maps.addAll(stringMap, intMap, toString);
    assertEquals(0, stringMap.size());
    
    intMap.put("foo", 6);
    Maps.addAll(stringMap, intMap, toString);
    assertEquals(1, stringMap.size());
    assertEquals("6", stringMap.get("foo"));

    intMap.clear();
    intMap.put("bar", 3);
    Maps.addAll(stringMap, intMap, toString);
    assertEquals(2, stringMap.size());
    assertEquals("6", stringMap.get("foo"));
    assertEquals("3", stringMap.get("bar"));

    intMap.clear();
    intMap.put("bar", 5);
    intMap.put("baz", 9);
    Maps.addAll(stringMap, intMap, toString);
    assertEquals(3, stringMap.size());
    assertEquals("6", stringMap.get("foo"));
    assertEquals("5", stringMap.get("bar"));
    assertEquals("9", stringMap.get("baz"));
  }
}
