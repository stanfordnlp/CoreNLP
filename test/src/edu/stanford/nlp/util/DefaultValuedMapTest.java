package edu.stanford.nlp.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.ClassicCounter;

import junit.framework.Assert;
import junit.framework.TestCase;

public class DefaultValuedMapTest extends TestCase {

  public void testArrayListValuedMap() {
    Map<String, List<Integer>> map = DefaultValuedMap.arrayListValuedMap();
    map.get("hello").add(1);
    map.get("world").add(42);
    map.get("hello").add(2);
    Assert.assertEquals(map.get("hello"), Arrays.asList(new Integer[]{1, 2}));
    Assert.assertEquals(map.get("world"), Arrays.asList(new Integer[]{42}));
    Assert.assertEquals(map.get("!"), new ArrayList<Integer>());
  }

  public void testHashSetValuedMap() {
    Map<Integer, Set<Double>> map = DefaultValuedMap.hashSetValuedMap();
    map.get(1).add(0.5);
    map.get(42).add(0.75);
    map.get(42).add(-2.0);
    Assert.assertEquals(map.get(1), new HashSet<Double>(Arrays.asList(new Double[]{0.5})));
    Assert.assertEquals(map.get(42), new HashSet<Double>(Arrays.asList(new Double[]{0.75, -2.0})));
    Assert.assertEquals(map.get(0), new HashSet<Double>());
  }

  public void testHashMapValuedMap() {
    Map<Integer, Map<String, Double>> map = DefaultValuedMap.hashMapValuedMap();
    map.get(1).put("a", 0.5);
    map.get(1).put("a", 1.5);
    map.get(42).put("b", 0.75);
    map.get(42).put("c", -2.0);
    Assert.assertEquals(map.get(1).get("c"), null);
    Assert.assertEquals(map.get(1).get("a"), 1.5);
    Assert.assertEquals(map.get(42).get("b"), 0.75);
    Assert.assertEquals(map.get(42).get("c"), -2.0);
    Assert.assertEquals(map.get(0).get("a"), null);
  }

  public void testCounterValuedMap() {
    Map<String, Counter<String>> map = DefaultValuedMap.counterValuedMap();
    map.get("b").incrementCount("X");
    map.get("a").incrementCount("Y");
    map.get("a").incrementCount("Y", 2);
    map.get("b").incrementCount("Y", 2);
    Assert.assertEquals(map.get("a"), Counters.asCounter(Arrays.asList("Y Y Y".split(" "))));
    Assert.assertEquals(map.get("b"), Counters.asCounter(Arrays.asList("X Y Y".split(" "))));
    Assert.assertEquals(map.get("C"), new ClassicCounter<String>());
  }
  
  public void testAsDefault() {
    Factory<String> factory = new Factory<String>() {
      private static final long serialVersionUID = 3715898146232279895L;
      public String create() {
        return "hello world";
      }};
    DefaultValuedMap<String,String> dvm1 = new DefaultValuedMap<String,String>(factory);
    DefaultValuedMap<Integer,DefaultValuedMap<String,String>> dvm2 = dvm1.asDefault();
    DefaultValuedMap<String,DefaultValuedMap<Integer,DefaultValuedMap<String,String>>> dvm3 = dvm2.asDefault();
    Assert.assertEquals("hello world", dvm1.get("foo"));
    Assert.assertEquals("hello world", dvm2.get(42).get("baz"));
    Assert.assertEquals("hello world", dvm3.get("spam").get(13).get("badger"));
    
    // make sure automatically created DefaultValuedMaps are new instances, not copies
    dvm1.put("X", "Y");
    Assert.assertEquals("hello world", dvm2.get(42).get("X"));
    Assert.assertEquals("hello world", dvm2.get(-1).get("X"));
  }
}
