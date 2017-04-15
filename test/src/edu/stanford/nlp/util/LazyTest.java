package edu.stanford.nlp.util;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * A simple test for the {@link Lazy} class.
 *
 * @author Gabor Angeli
 */
public class LazyTest {

  @Test
  public void testFrom() {
    Lazy<String> x = Lazy.from("foo");
    assertEquals("foo", x.getIfDefined());
    assertEquals("foo", x.get());
  }


  @Test
  public void testOf() {
    Lazy<String> x = Lazy.of(() -> "foo");
    assertEquals(null, x.getIfDefined());
    assertEquals("foo", x.get());
  }



  @Test
  public void testCached() {
    Lazy<String> x = Lazy.cache(() -> "foo");
    assertEquals(null, x.getIfDefined());
    assertEquals("foo", x.get());
  }


  @Test
  public void testOfCalledOnlyOnce() {
    AtomicInteger callCount = new AtomicInteger(0);
    Lazy<String> x = Lazy.of(() -> {
      callCount.incrementAndGet();
      return "foo";
    });
    assertEquals(null, x.getIfDefined());
    assertEquals("foo", x.get());
    x.simulateGC();
    assertEquals("foo", x.get());
    assertEquals(1, callCount.get());
  }


  @Test
  public void testCachedCalledOnlyOnce() {
    AtomicInteger callCount = new AtomicInteger(0);
    Lazy<String> x = Lazy.cache(() -> {
      callCount.incrementAndGet();
      return "foo";
    });
    assertEquals(null, x.getIfDefined());
    assertEquals("foo", x.get());
    assertEquals("foo", x.get());
    assertEquals(1, callCount.get());
  }


  @Test
  public void testCachedGC() {
    AtomicInteger callCount = new AtomicInteger(0);
    Lazy<String> x = Lazy.cache(() -> {
      callCount.incrementAndGet();
      return "foo";
    });
    assertEquals(null, x.getIfDefined());
    assertEquals("foo", x.get());
    x.simulateGC();
    assertEquals("foo", x.get());
    assertEquals(2, callCount.get());
  }


}