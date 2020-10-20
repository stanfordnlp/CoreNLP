package edu.stanford.nlp.util;

import junit.framework.TestCase;

public class ReflectionLoadingTest extends TestCase {
  public void testOneArg() {
    String s = ReflectionLoading.loadByReflection("java.lang.String",
                                                  "foo");
    assertEquals("foo", s);
  }

  public void testNoArgs() {
    String s = ReflectionLoading.loadByReflection("java.lang.String");
    assertEquals("", s);
  }
}
