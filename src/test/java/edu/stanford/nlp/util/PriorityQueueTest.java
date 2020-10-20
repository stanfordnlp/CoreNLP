package edu.stanford.nlp.util;

import junit.framework.TestCase;

/**
 * @author Christopher Manning
 */
public class PriorityQueueTest extends TestCase {


  public void testBinaryHeapPriorityQueue() {
    runBasicTests("edu.stanford.nlp.util.BinaryHeapPriorityQueue");
    runRelaxingTests("edu.stanford.nlp.util.BinaryHeapPriorityQueue");
  }

  public void testFixedPrioritiesPriorityQueue() {
    runBasicTests("edu.stanford.nlp.util.FixedPrioritiesPriorityQueue");
    runNotRelaxingTests("edu.stanford.nlp.util.FixedPrioritiesPriorityQueue");
  }

  private static void runBasicTests(String className) {
    PriorityQueue<String> queue;
    try {
      queue = ErasureUtils.uncheckedCast(Class.forName(className).newInstance());
    } catch (Exception e) {
      fail(e.toString());
      return;
    }
    runBasicTests(queue);
  }

  protected static void runBasicTests(PriorityQueue<String> queue) {
    queue.add("a", 1.0);
    assertEquals("Added a:1", "[a=1.0]", queue.toString());
    queue.add("b", 2.0);
    assertEquals("Added b:2", "[b=2.0, a=1.0]", queue.toString());
    queue.add("c", 1.5);
    assertEquals("Added c:1.5", "[b=2.0, c=1.5, a=1.0]", queue.toString());

    assertEquals("removeFirst()", "b", queue.removeFirst());
    assertEquals("queue", "[c=1.5, a=1.0]", queue.toString());
    assertEquals("removeFirst()", "c", queue.removeFirst());
    assertEquals("queue", "[a=1.0]", queue.toString());
    assertEquals("removeFirst()", "a", queue.removeFirst());
    assertTrue(queue.isEmpty());
  }

  private static void runRelaxingTests(String className) {
    BinaryHeapPriorityQueue<String> queue;
    try {
      queue = ErasureUtils.uncheckedCast(Class.forName(className).newInstance());
    } catch (Exception e) {
      fail(e.toString());
      return;
    }
    runRelaxingTests(queue);
  }

  protected static void runRelaxingTests(BinaryHeapPriorityQueue<String> queue) {
    queue.add("a", 1.0);
    assertEquals("Added a:1", "[a=1.0]", queue.toString());
    queue.add("b", 2.0);
    assertEquals("Added b:2", "[b=2.0, a=1.0]", queue.toString());
    queue.add("c", 1.5);
    assertEquals("Added c:1.5", "[b=2.0, c=1.5, a=1.0]", queue.toString());

    queue.relaxPriority("a", 3.0);
    assertEquals("Increased a to 3", "[a=3.0, b=2.0, c=1.5]", queue.toString());
    queue.decreasePriority("b", 0.0);
    assertEquals("Decreased b to 0", "[a=3.0, c=1.5, b=0.0]", queue.toString());

    assertEquals("removeFirst()", "a", queue.removeFirst());
    assertEquals("queue", "[c=1.5, b=0.0]", queue.toString());
    assertEquals("removeFirst()", "c", queue.removeFirst());
    assertEquals("queue", "[b=0.0]", queue.toString());
    assertEquals("removeFirst()", "b", queue.removeFirst());
    assertTrue(queue.isEmpty());
  }

  private static void runNotRelaxingTests(String className) {
    FixedPrioritiesPriorityQueue<String> pq;
    try {
      pq = ErasureUtils.uncheckedCast(Class.forName(className).newInstance());
    } catch (Exception e) {
      fail(e.toString());
      return;
    }
    assertEquals("[]", pq.toString());
    pq.add("one",1);
    assertEquals("[one=1.0]", pq.toString());
    pq.add("three",3);
    assertEquals("[three=3.0, one=1.0]", pq.toString());
    pq.add("one",1.1);
    assertEquals("[three=3.0, one=1.1, one=1.0]", pq.toString());
    pq.add("two",2);
    assertEquals("[three=3.0, two=2.0, one=1.1, one=1.0]", pq.toString());
    assertEquals("[three=3.000, two=2.000, ...]", pq.toString(2));

    FixedPrioritiesPriorityQueue<String> clone = pq.clone();
    assertEquals(3.0, clone.getPriority());
    assertEquals(pq.next(), clone.next());
    assertEquals(2.0, clone.getPriority());
    assertEquals(pq.next(), clone.next());
    assertEquals(1.1, clone.getPriority());
    assertEquals(pq.next(), clone.next());
    assertEquals(1.0, clone.getPriority());
    assertEquals(pq.next(), clone.next());
    assertFalse(clone.hasNext());
    assertTrue(clone.isEmpty());
  }

}
