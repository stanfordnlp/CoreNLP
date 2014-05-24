package edu.stanford.nlp.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.junit.Assert.*;

/**
 *
 * Basic tests for the FileBackedCache
 *
 * @author Gabor Angeli
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
public class FileBackedCacheTest {

  private static class CustomHash implements Serializable {
    private int unique;
    private int hash;
    private CustomHash(int unique, int hash) {
      this.unique = unique;
      this.hash = hash;
    }
    public int hashCode() { return hash; }
    public boolean equals(Object o) {
      return (o instanceof CustomHash) && ((CustomHash) o).unique == unique;
    }
    public String toString() { return "CustomHash(id=" + unique + ", hash=" + hash + ")"; }
  }

  private FileBackedCache<String, String> cache;
  private FileBackedCache<Integer, Map<String, ArrayList<String>>> mapCache;

  @Before
  public void setUp() {
    try {
      // (regular cache)
      File cacheDir = File.createTempFile("cache", ".dir");
      cacheDir.delete();
      cache = new FileBackedCache<String, String>(cacheDir);
      assertEquals(0, cacheDir.listFiles().length);
      // (map cache)
      File mapCacheDir = File.createTempFile("cache", ".dir");
      mapCacheDir.delete();
      mapCache = new FileBackedCache<Integer, Map<String, ArrayList<String>>>(mapCacheDir);
      assertEquals(0, mapCacheDir.listFiles().length);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @After
  public  void tearDown() {
    if (cache.cacheDir.listFiles() != null) {
      for (File c : cache.cacheDir.listFiles()) {
        assertTrue(c.delete());
      }
      assertTrue(cache.cacheDir.delete());
    }
    if (mapCache.cacheDir.listFiles() != null) {
      for (File c : mapCache.cacheDir.listFiles()) {
        assertTrue(c.delete());
      }
      assertTrue(mapCache.cacheDir.delete());
    }
  }

  @Test
  public void testContainsLocal() {
    cache.put("key", "value");
    cache.put("key2", "value2");
    assertTrue(cache.containsKey("key"));
    assertTrue(cache.containsKey("key2"));
    assertTrue(FileBackedCache.locksHeld().isEmpty());
  }

  @Test
  public void testGetLocal() {
    cache.put("key", "value");
    cache.put("key2", "value2");
    assertEquals("value", cache.get("key"));
    assertEquals("value2", cache.get("key2"));
    assertTrue(FileBackedCache.locksHeld().isEmpty());
  }

  @Test
  public void testPutLocal() {
    assertEquals(null, cache.put("key", "value"));
    assertEquals(null, cache.put("key2", "value2"));
    assertTrue(FileBackedCache.locksHeld().isEmpty());
  }

  @Test
  public void testCacheWritingToDisk() {
    assertEquals(0, cache.cacheDir.listFiles().length);
    cache.put("key", "value");
    assertEquals(1, cache.cacheDir.listFiles().length);
    cache.put("key2", "value2");
    assertEquals(2, cache.cacheDir.listFiles().length);
    assertTrue(FileBackedCache.locksHeld().isEmpty());
  }

  @Test
  public void testSize() {
    assertEquals(0, cache.sizeInMemory());
    assertEquals(0, cache.size());
    cache.put("key", "value");
    cache.put("key2", "value2");
    assertEquals(2, cache.sizeInMemory()); // assume no GC
    assertEquals(2, cache.size());
    cache.clear();
    assertEquals(0, cache.sizeInMemory());
    assertEquals(2, cache.cacheDir.listFiles().length);
    assertEquals(2, cache.size());
    assertEquals(2, cache.sizeInMemory());
    assertTrue(FileBackedCache.locksHeld().isEmpty());
  }

  @Test
  public void testContainsFile() {
    cache.put("key", "value");
    cache.put("key2", "value2");
    cache.clear();
    assertTrue(cache.containsKey("key"));
    assertTrue(cache.containsKey("key2"));
    assertTrue(FileBackedCache.locksHeld().isEmpty());
  }

  @Test
  public void testContainsRemoveFromMemory() {
    cache.put("key", "value");
    cache.put("key2", "value2");
    cache.removeFromMemory("key");
    assertEquals(1, cache.sizeInMemory());
    assertTrue(cache.containsKey("key"));
    assertTrue(cache.containsKey("key2"));
    assertTrue(FileBackedCache.locksHeld().isEmpty());
  }

  @Test
  public void testPutFile() {
    // Case 1: simple put
    cache.put("key", "value");
    assertEquals("value", cache.put("key", "valueReplaced"));
    assertEquals("valueReplaced", cache.get("key"));
    // Case 2: put then clear
    cache.put("key", "value");
    cache.clear();
    assertEquals("value", cache.put("key", "valueReplaced"));
    cache.clear();
    assertEquals("valueReplaced", cache.get("key"));
    assertTrue(FileBackedCache.locksHeld().isEmpty());
  }

  @Test
  public void testGetFile() {
    cache.put("key", "value");
    cache.put("key2", "value2");
    cache.clear();
    assertEquals("value", cache.get("key"));
    assertEquals("value2", cache.get("key2"));
    assertTrue(FileBackedCache.locksHeld().isEmpty());
  }

  @Test
  public void testIterator() {
    cache.put("key", "value");
    cache.put("key2", "value2");
    int count = 0;
    Iterator<Map.Entry<String,String>> iterator = cache.iterator();
    while(iterator.hasNext()) {
      Map.Entry<String, String> entry = iterator.next();
      if (entry.getKey() == "key") assertEquals("value", entry.getValue());
      if (entry.getKey() == "key2") assertEquals("value2", entry.getValue());
      count += 1;
    }
    assertEquals(2, count);
    assertTrue(FileBackedCache.locksHeld().isEmpty());
  }

  @Test
  public void testComprehension() {
    cache.put("key", "value");
    cache.put("key2", "value2");
    int count = 0;
    for (Map.Entry<String, String> entry : cache) {
      if (entry.getKey() == "key") assertEquals("value", entry.getValue());
      if (entry.getKey() == "key2") assertEquals("value2", entry.getValue());
      count += 1;
    }
    assertEquals(2, count);
    assertTrue(FileBackedCache.locksHeld().isEmpty());
  }

  @Test
  public void testCollision() throws IOException {
    // Custom Setup
    File cacheDir = File.createTempFile("cache", ".dir");
    cacheDir.delete();
    FileBackedCache<CustomHash, String> myCache = new FileBackedCache<CustomHash, String>(cacheDir);
    assertEquals(0, cacheDir.listFiles().length);

    // Test
    myCache.put(new CustomHash(0, 0), "zero");
    myCache.put(new CustomHash(1, 0), "one");
    myCache.put(new CustomHash(1, 1), "one'");
    assertEquals("zero", myCache.get(new CustomHash(0, 0)));
    assertEquals("one", myCache.get(new CustomHash(1, 0)));
    assertEquals("one'", myCache.get(new CustomHash(1, 1)));
    myCache.clear();
    assertEquals(0, myCache.sizeInMemory());
    assertEquals("zero", myCache.get(new CustomHash(0, 0)));
    assertEquals("one", myCache.get(new CustomHash(1, 0)));
    assertEquals("one'", myCache.get(new CustomHash(1, 1)));

    // Retest
    FileBackedCache<CustomHash, String> reload = new FileBackedCache<CustomHash, String>(cacheDir);
    assertEquals("zero", reload.get(new CustomHash(0, 0)));
    assertEquals("one",  reload.get(new CustomHash(1, 0)));
    assertEquals("one'", reload.get(new CustomHash(1, 1)));
    reload.put(new CustomHash(2, 0), "two");
    assertEquals("two", reload.get(new CustomHash(2, 0)));

    // Custom Teardown
    for (File c : cache.cacheDir.listFiles()) {
      assertTrue(c.delete());
    }
    assertTrue(cache.cacheDir.delete());
    assertTrue(FileBackedCache.locksHeld().isEmpty());
  }

  @SuppressWarnings({"unchecked", "ConstantConditions"})
  @Test
  public void testMerge() throws IOException {
    cache.put("key", "value");

    // (create constituents)
    File constituent1File = File.createTempFile("cache", ".dir");
    assertTrue(constituent1File.delete());
    FileBackedCache<String, String> constituent1 = new FileBackedCache<String, String>(constituent1File);
    File constituent2File = File.createTempFile("cache", ".dir");
    assertTrue(constituent2File.delete());
    FileBackedCache<String, String> constituent2 = new FileBackedCache<String, String>(constituent2File);

    // (populate constituents)
    constituent1.put("c1Key1", "constituent1a");
    constituent1.put("c1Key2", "constituent1b");
    constituent1.put("c1Key3", "overlap");
    constituent2.put("c2Key1", "constituent2a");
    constituent2.put("c2Key2", "constituent2b");
    constituent2.put("c1Key3", "overlapReplaced");
    constituent1.clear();
    constituent2.clear();

    // (merge)
    FileBackedCache.merge(cache, new FileBackedCache[]{constituent1, constituent2});
    assertEquals("value", cache.get("key"));

    // (checks)
    cache.clear();
    assertEquals("constituent1a", cache.get("c1Key1"));
    assertEquals("constituent1b", cache.get("c1Key2"));
    assertEquals("constituent2a", cache.get("c2Key1"));
    assertEquals("constituent2b", cache.get("c2Key2"));
    assertEquals("overlapReplaced", cache.get("c1Key3"));

    // (clean up)
    if (constituent1File.listFiles() != null) {
      for (File c : constituent1File.listFiles()) {
        assertTrue(c.delete());
      }
      assertTrue(constituent1File.delete());
    }
    if (constituent2File.listFiles() != null) {
      for (File c : constituent2File.listFiles()) {
        assertTrue(c.delete());
      }
      assertTrue(constituent2File.delete());
    }
    assertTrue(FileBackedCache.locksHeld().isEmpty());
  }

  @Test
  public void testMapValueGoodPattern() throws IOException {
    HashMap<String, ArrayList<String>> map = new HashMap<String, ArrayList<String>>();
    map.put("foo", new ArrayList<String>());
    mapCache.put(42, map);
    assertEquals(1, mapCache.get(42).size());
    mapCache.clear();
    assertEquals(1, mapCache.get(42).size());
  }
}
