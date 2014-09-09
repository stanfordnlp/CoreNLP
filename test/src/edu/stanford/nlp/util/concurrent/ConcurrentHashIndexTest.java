package edu.stanford.nlp.util.concurrent;

import junit.framework.TestCase;

import java.util.Iterator;
import java.util.List;

import edu.stanford.nlp.util.Index;

/**
 * @author Spence Green
 */
public class ConcurrentHashIndexTest extends TestCase  {

  protected Index<String> index;
  protected Index<String> index2;
  protected Index<String> index3;

  @Override
  protected void setUp() {
    index = new ConcurrentHashIndex<String>();
    index.add("The");
    index.add("Beast");
    index2 = new ConcurrentHashIndex<String>();
    index2.add("Beauty");
    index2.add("And");
    index2.add("The");
    index2.add("Beast");
    index3 = new ConcurrentHashIndex<String>();
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
    assertEquals(2, strs.length);
    assertTrue(index.contains(strs[0]));
    assertTrue(index.contains(strs[1]));
  }

  public void testObjects() {
    List<String> foo = (List<String>) index2.objects(new int[] {0, 3});
    assertEquals("Beauty", foo.get(0));
    assertEquals("Beast", foo.get(1));
  }

}
