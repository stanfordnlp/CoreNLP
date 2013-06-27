package edu.stanford.nlp.util;

import junit.framework.TestCase;

/**
 * @author John Bauer
 */
public class DeltaIndexTest extends TestCase  {
  HashIndex<String> underlying;
  DeltaIndex<String> spillover;

  public void setUp() {
    underlying = new HashIndex<String>();
    underlying.add("foo0");
    underlying.add("foo1");
    underlying.add("foo2");
    underlying.add("foo3");
    underlying.add("foo4");
    assertEquals(5, underlying.size());

    spillover = new DeltaIndex<String>(underlying);
    spillover.add("foo1");
    spillover.add("foo5");
    spillover.add("foo6");
  }

  public void testSize() {
    assertEquals(5, underlying.size());
    assertEquals(7, spillover.size());
  }

  public void testContains() {
    assertTrue(underlying.contains("foo1"));
    assertFalse(underlying.contains("foo5"));
    assertFalse(underlying.contains("foo7"));

    assertTrue(spillover.contains("foo1"));
    assertTrue(spillover.contains("foo5"));
    assertFalse(spillover.contains("foo7"));
  }

  public void testIndex() {
    assertEquals(4, spillover.indexOf("foo4"));
    assertEquals(6, spillover.indexOf("foo6"));
    assertEquals(-1, spillover.indexOf("foo7"));
  }

  public void testGet() {
    assertEquals("foo4", spillover.get(4));
    assertEquals("foo5", spillover.get(5));
    assertEquals("foo6", spillover.get(6));
  }
}
