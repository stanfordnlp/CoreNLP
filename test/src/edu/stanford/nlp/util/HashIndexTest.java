package edu.stanford.nlp.util;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * @author Sebastian Riedel
 */
public class HashIndexTest extends TestCase  {

  protected Index<String> index;
  protected Index<String> index2;
  protected Index<String> index3;

  @Override
  protected void setUp() {
    index = new HashIndex<String>();
    index.add("The");
    index.add("Beast");
    index2 = new HashIndex<String>();
    index2.add("Beauty");
    index2.add("And");
    index2.add("The");
    index2.add("Beast");
    index3 = new HashIndex<String>();
    index3.add("Markov");
    index3.add("The");
    index3.add("Beast");
  }

  public void testSize() {
    assertEquals(2,index.size());
  }

  public void testGet() {
    assertEquals(2,index.size());
    assertEquals("The",index.get(0));
    assertEquals("Beast",index.get(1));
  }

  public void testIndexOf() {
    assertEquals(2,index.size());
    assertEquals(0,index.indexOf("The"));
    assertEquals(1,index.indexOf("Beast"));
  }

  public void testIterator() {
    Iterator<String> i = index.iterator();
    assertEquals("The",i.next());
    assertEquals("Beast",i.next());
    assertEquals(false,i.hasNext());
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

  public void testToArray() {
    String[] strs = new String[2];
    strs = index.objectsList().toArray(strs);
    assertEquals("The", strs[0]);
    assertEquals("Beast", strs[1]);
    assertEquals(2, strs.length);
  }

  public void testUnmodifiableViewEtc() {
    List<String> list = new ArrayList<String>();
    list.add("A");
    list.add("B");
    list.add("A");
    list.add("C");
    HashIndex<String> index4 = new HashIndex<String>(list);
    HashIndex<String> index5 = new HashIndex<String>();
    index5.addAll(list);
    assertEquals("Equality failure", index4, index5);
    index5.addToIndex("D");
    index5.addToIndex("E");
    index5.indexOf("F");
    index5.addAll(list);
    assertEquals(5, index5.size());
    assertEquals(3, index4.size());
    assertTrue(index4.contains("A"));
    assertEquals(0, index4.indexOf("A"));
    assertEquals(1, index4.indexOf("B"));
    assertEquals(2, index4.indexOf("C"));
    assertEquals("A", index4.get(0));
    Index<String> index4u = index4.unmodifiableView();
    assertEquals(3, index4u.size());
    assertTrue(index4u.contains("A"));
    assertEquals(0, index4u.indexOf("A"));
    assertEquals(1, index4u.indexOf("B"));
    assertEquals(2, index4u.indexOf("C"));
    assertEquals("A", index4u.get(0));
    assertEquals(-1, index4u.addToIndex("D"));
    boolean okay = false;
    try {
      index4u.unlock();
    } catch (UnsupportedOperationException uoe) {
      okay = true;
    } finally {
      assertTrue(okay);
    }
  }


  public void testCopyConstructor() {
    Index<String> test = new HashIndex<String>();
    test.add("Beauty");
    test.add("And");
    test.add("The");
    test.add("Beast");


    HashIndex<String> copy = new HashIndex<String>(test);
    assertEquals(test, copy);
  }
}
