package edu.stanford.nlp.util;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import junit.framework.TestCase;
import edu.stanford.nlp.util.CoreMapTest.IntegerA;

/**
 * Tests for the CoreMaps utilities class.
 *
 * @author dramage
 */
public class CoreMapsTest extends TestCase {

  /** Tests the map view and extraction */
  public void testMaps() {
    Random random = new Random();

    List<CoreMap> maps = new LinkedList<>();
    for (int i = 0; i < 25; i++) {
      ArrayCoreMap m = new ArrayCoreMap();
      m.set(CoreMapTest.IntegerA.class, random.nextInt());
      maps.add(m);
    }

    Map<CoreMap,Integer> view = CoreMaps.asMap(maps, IntegerA.class);

    // test getting and setting
    for (CoreMap map : maps) {
      assertTrue(view.containsKey(map));
      assertEquals(view.get(map), map.get(IntegerA.class));

      Integer v = random.nextInt();

      map.set(IntegerA.class, v);

      assertEquals(view.get(map), v);
      assertEquals(view.get(map), map.get(IntegerA.class));
    }

    // test iterating and set views
    assertEquals(new LinkedList<CoreMap>(view.keySet()), maps);
    for (Map.Entry<CoreMap, Integer> entry : view.entrySet()) {
      assertEquals(entry.getKey().get(IntegerA.class), entry.getValue());

      Integer v = random.nextInt();

      entry.setValue(v);
      assertEquals(entry.getValue(), v);
      assertEquals(entry.getKey().get(IntegerA.class), v);
    }
  }

}
