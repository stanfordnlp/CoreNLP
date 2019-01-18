package edu.stanford.nlp.util;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * @author Sebastian Riedel
 */
public class HashIndexTest {

  protected Index<String> index;
  // private Index<String> index2;
  // private Index<String> index3;

  @Before
  public void setUp() {
    index = new HashIndex<>();
    index.add("The");
    index.add("Beast");
    /*
    index2 = new HashIndex<>();
    index2.add("Beauty");
    index2.add("And");
    index2.add("The");
    index2.add("Beast");
    index3 = new HashIndex<>();
    index3.add("Markov");
    index3.add("The");
    index3.add("Beast");
    */
  }

  @Test
  public void testSize() {
    Assert.assertEquals(2, index.size());
  }

  @Test
  public void testGet() {
    Assert.assertEquals(2, index.size());
    Assert.assertEquals("The", index.get(0));
    Assert.assertEquals("Beast", index.get(1));
  }

  @Test
  public void testIndexOf() {
    Assert.assertEquals(2, index.size());
    Assert.assertEquals(0, index.indexOf("The"));
    Assert.assertEquals(1, index.indexOf("Beast"));
  }

  @Test
  public void testIterator() {
    Iterator<String> i = index.iterator();
    Assert.assertEquals("The", i.next());
    Assert.assertEquals("Beast", i.next());
    Assert.assertFalse(i.hasNext());
  }

  /*
  public void testRemove() {
    index2.remove("Sebastian");
    index2.remove("Beast");
    assertEquals(3, index2.size());
    assertEquals(0, index2.indexOf("Beauty"));
    assertEquals(1, index2.indexOf("And"));
    assertEquals(3, index2.indexOf("Beast"));
    index2.removeAll(index3.objectsList());
  }
  */

  @Test
  public void testToArray() {
    String[] strs = new String[2];
    strs = index.objectsList().toArray(strs);
    Assert.assertEquals("The", strs[0]);
    Assert.assertEquals("Beast", strs[1]);
    Assert.assertEquals(2, strs.length);
  }

  @Test
  public void testUnmodifiableViewEtc() {
    List<String> list = new ArrayList<>();
    list.add("A");
    list.add("B");
    list.add("A");
    list.add("C");
    HashIndex<String> index4 = new HashIndex<>(list);
    HashIndex<String> index5 = new HashIndex<>();
    index5.addAll(list);
    Assert.assertEquals("Equality failure", index4, index5);
    index5.addToIndex("D");
    index5.addToIndex("E");
    index5.indexOf("F");
    index5.addAll(list);
    Assert.assertEquals(5, index5.size());
    Assert.assertEquals(3, index4.size());
    Assert.assertTrue(index4.contains("A"));
    Assert.assertEquals(0, index4.indexOf("A"));
    Assert.assertEquals(1, index4.indexOf("B"));
    Assert.assertEquals(2, index4.indexOf("C"));
    Assert.assertEquals("A", index4.get(0));
    Index<String> index4u = index4.unmodifiableView();
    Assert.assertEquals(3, index4u.size());
    Assert.assertTrue(index4u.contains("A"));
    Assert.assertEquals(0, index4u.indexOf("A"));
    Assert.assertEquals(1, index4u.indexOf("B"));
    Assert.assertEquals(2, index4u.indexOf("C"));
    Assert.assertEquals("A", index4u.get(0));
    Assert.assertEquals(-1, index4u.addToIndex("D"));
    boolean okay = false;
    try {
      index4u.unlock();
    } catch (UnsupportedOperationException uoe) {
      okay = true;
    } finally {
      Assert.assertTrue(okay);
    }
  }


  @Test
  public void testCopyConstructor() {
    Index<String> test = new HashIndex<>();
    test.add("Beauty");
    test.add("And");
    test.add("The");
    test.add("Beast");

    HashIndex<String> copy = new HashIndex<>(test);
    Assert.assertEquals(test, copy);
  }

}
