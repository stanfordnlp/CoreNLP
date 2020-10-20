package edu.stanford.nlp.util;

import junit.framework.TestCase;

/**
 * Tests some basic operations on the TreeShapedStack
 *
 * @author Danqi Chen
 */
public class TreeShapedStackTest extends TestCase {

  public void testEquals() {
    TreeShapedStack<String> t1 = new TreeShapedStack<>();
    t1 = t1.push("foo");
    t1 = t1.push("bar");
    t1 = t1.push("bar");
    t1 = t1.push("diet");
    t1 = t1.push("coke");

    TreeShapedStack<String> t2 = new TreeShapedStack<>();
    t2 = t2.push("foo");
    t2 = t2.push("bar");
    t2 = t2.push("bar");
    t2 = t2.push("diet");
    t2 = t2.push("coke");

    TreeShapedStack<String> t3 = t2.pop().push("pepsi");

    assertEquals(t1, t2);
    assertFalse(t1.pop().equals(t2));
    assertFalse(t2.pop().equals(t1));
    assertFalse(t2.equals(t3));
  }

  public void testBasicOperations() {
    TreeShapedStack<String> tss = new TreeShapedStack<>();
    assertEquals(tss.size, 0);

    TreeShapedStack<String> tss1 = tss.push("1");
    assertEquals(tss1.size, 1);
    assertEquals(tss1.peek(), "1");

    TreeShapedStack<String> tss2 = tss1.push("2");
    assertEquals(tss2.size, 2);
    assertEquals(tss2.peek(), "2");
    assertEquals(tss2.previous.peek(), "1");

    TreeShapedStack<String> tss3 = tss2.push("3");
    assertEquals(tss3.size, 3);
    assertEquals(tss3.peek(), "3");
    assertEquals(tss3.previous.peek(), "2");

    tss3 = tss3.pop();
    assertEquals(tss3.peek(), "2");
    assertEquals(tss3.previous.peek(), "1");

    assertEquals(tss3.peek(), "2");

    TreeShapedStack<String> tss4 = tss3.push("4");
    assertEquals(tss4.peek(), "4");
    assertEquals(tss4.peek(), "4");
    assertEquals(tss4.previous.peek(), "2");

    tss4 = tss4.pop();
    assertEquals(tss4.peek(), "2");
    tss4 = tss4.pop();
    assertEquals(tss4.peek(), "1");
    tss4 = tss4.pop();
    assertEquals(tss4.size, 0);
  }
}
