package edu.stanford.nlp.util;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


/**
 * @author Sebastian Riedel
 * @author Christoopher Manning
 */
public class CircleListTest extends TestCase {

  private CircleList<String> list;
  private CircleList<String> list2;

  @Override
  protected void setUp() {
    ArrayList<String> strings = new ArrayList<String>();
    strings.add("the");
    strings.add("man");
    list = new CircleList<String>(strings);
    List<String> strings2 = Arrays.asList("one", "two", "three", "four", "five");
    list2 = new CircleList<String>(strings2);
  }

  public void testGet() {
    assertEquals("the", list.get(0));
    assertEquals("man", list.get(1));
    assertEquals(list.get(0), list.get(2));
    assertEquals(list.get(1), list.get(3));
    assertEquals(list.get(2), list.get(4));
    assertEquals(list.get(-1), list.get(1));
  }

  public void testSize() {
    assertEquals("List size wrong!", 2, list.size());
  }

  public void testSubList() {
    assertEquals(Collections.singletonList("man"), list.subList(1,2));
    assertEquals(Collections.singletonList("man"), list.subList(1,4));
    assertEquals(Arrays.asList("two", "three", "four"), list2.subList(1,4));
    assertEquals(Arrays.asList("five", "one", "two"), list2.subList(4, 2));
    assertEquals(Arrays.asList("five", "one", "two"), list2.subList(4, 7));
    assertEquals(Arrays.asList("five", "one", "two"), list2.subList(-1, 7));
    assertEquals(Arrays.asList("five", "one", "two"), list2.subList(4, -3));
  }

}
