package edu.stanford.nlp.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class LeastRecentlyUsedCacheTest {
  /**
   * Test basic operations.  Add a few items, check that they are all
   * in the cache, make sure unknown items aren't there.
   */
  @Test
  public void testBasic() {
    LeastRecentlyUsedCache<String, Integer> cache = new LeastRecentlyUsedCache<>(100);
    cache.add("foo", 2);
    cache.add("bar", 3);
    cache.add("baz", 4);

    assertEquals(3, cache.size());

    assertEquals(2, cache.getOrDefault("foo", 0).intValue());
    assertEquals(3, cache.getOrDefault("bar", 0).intValue());
    assertEquals(4, cache.getOrDefault("baz", 0).intValue());
    assertEquals(0, cache.getOrDefault("unbanmoxopal", 0).intValue());
  }

  /**
   * Check that if the cache is filled up, items start being dropped.
   */
  @Test
  public void testOverflow() {
    LeastRecentlyUsedCache<String, Integer> cache = new LeastRecentlyUsedCache<>(4);
    cache.add("1", 1);
    cache.add("2", 2);
    cache.add("3", 3);
    cache.add("4", 4);
    cache.add("5", 5);

    assertEquals(4, cache.size());
    assertEquals(0, cache.getOrDefault("1", 0).intValue());
    assertEquals(2, cache.getOrDefault("2", 0).intValue());
  }

  /**
   * Check that testing items moves them to the back of the queue to be deleted.
   */
  @Test
  public void testReorder() {
    LeastRecentlyUsedCache<String, Integer> cache = new LeastRecentlyUsedCache<>(4);
    cache.add("1", 1);
    cache.add("2", 2);
    cache.add("3", 3);
    cache.add("4", 4);
    cache.add("5", 5);

    assertEquals(4, cache.size());
    assertEquals(0, cache.getOrDefault("1", 0).intValue());
    assertEquals(2, cache.getOrDefault("2", 0).intValue());

    // after testing for the existence of 2, 2 will have been moved to
    // the back of the queue to be dropped
    cache.add("unbanmoxopal", 6);
    assertEquals(4, cache.size());
    assertEquals(0, cache.getOrDefault("1", 0).intValue());
    assertEquals(2, cache.getOrDefault("2", 2).intValue());
    // 3 should be dropped instead
    assertEquals(0, cache.getOrDefault("3", 0).intValue());
  }
}
