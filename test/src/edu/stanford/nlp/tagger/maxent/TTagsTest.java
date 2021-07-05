package edu.stanford.nlp.tagger.maxent;


import edu.stanford.nlp.util.Generics;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

public class TTagsTest {

  private TTags tt;

  @Before
  public void setUp() {
    tt = new TTags();
  }

  @Test
  public void testUniqueness() {
    int a = tt.add("one");
    int b = tt.add("two");
    Assert.assertTrue(a != b);
  }

  @Test
  public void testSameness() {
    int a = tt.add("goat");
    int b = tt.add("goat");
    Assert.assertEquals(a, b);
  }

  @Test
  public void testPreservesString() {
    int a = tt.add("monkey");
    String s = tt.getTag(a);
    Assert.assertEquals(s, "monkey");
  }

  @Test
  public void testPreservesIndex() {
    int a = tt.add("spunky");
    int b = tt.getIndex("spunky");
    Assert.assertEquals(a, b);
  }

  @Test
  public void testCanCount() {
    int s = tt.getSize();
    tt.add("asdfdsaefasfdsaf");
    int s2 = tt.getSize();
    Assert.assertEquals(s + 1, s2);
  }

  @Test
  public void testHoldsLotsOfStuff() {
    try {
      for(int i = 0; i < 1000; i++) {
        tt.add("fake" + i);
      }
    } catch(Exception e) {
      Assert.fail("couldn't put lots of stuff in:" + e.getMessage());
    }
  }

  @Test
  public void testClosed() {
    tt.add("java");

    Assert.assertFalse(tt.isClosed("java"));
    tt.markClosed("java");
    Assert.assertTrue(tt.isClosed("java"));
  }

  @Test
  public void testSerialization() {
    for(int i = 0; i < 100; i++) {
      tt.add("fake" + i);
    }
    tt.markClosed("fake44");
    tt.add("boat");
    tt.save("testoutputfile", Generics.newHashMap());
    TTags t2 = new TTags();
    t2.read("testoutputfile");
    Assert.assertEquals(tt.getSize(), t2.getSize());
    Assert.assertEquals(tt.getIndex("boat"), t2.getIndex("boat"));
    Assert.assertEquals(t2.getTag(tt.getIndex("boat")), "boat");

    Assert.assertFalse(t2.isClosed("fake43"));
    Assert.assertTrue(t2.isClosed("fake44"));

    Assert.assertTrue((new File("testoutputfile")).delete());
  }

}
