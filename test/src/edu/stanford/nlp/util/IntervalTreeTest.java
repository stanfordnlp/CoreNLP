package edu.stanford.nlp.util;

import junit.framework.TestCase;

import java.util.*;

/**
 * Test for the interval tree.
 *
 * @author Angel Chang
 */
public class IntervalTreeTest extends TestCase {

  private static void checkOverlapping(Collection<Interval<Integer>> all,
                                       Collection<Interval<Integer>> overlapping,
                                       Interval<Integer> target) {
    for (Interval<Integer> interval: all) {
      assertNotNull(interval);
    }
    for (Interval<Integer> interval: overlapping) {
      assertTrue(interval.overlaps(target));
    }
    List<Interval<Integer>> rest = new ArrayList<>(all);
    rest.removeAll(overlapping);
    for (Interval<Integer> interval: rest) {
      assertNotNull(interval);
      assertFalse("Should not overlap: " + interval + " with " + target, interval.overlaps(target));
    }
  }

  public void testGetOverlapping() throws Exception
  {
    Interval<Integer> a = Interval.toInterval(249210699, 249212659);
    Interval<Integer> before = Interval.toInterval(249210000, 249210600);
    Interval<Integer> included = Interval.toInterval(249210800, 249212000);
    Interval<Integer> after = Interval.toInterval(249213000, 249214000);

    IntervalTree<Integer, Interval<Integer>> tree = new IntervalTree<>();
    tree.add(a);

    List<Interval<Integer>> overlapping1 = tree.getOverlapping(before);
    assertTrue(overlapping1.isEmpty());
    List<Interval<Integer>> overlapping2 = tree.getOverlapping(included);
    assertTrue(overlapping2.size() == 1);
    List<Interval<Integer>> overlapping3 = tree.getOverlapping(after);
    assertTrue(overlapping3.isEmpty());

    // Remove a
    tree.remove(a);
    assertTrue(tree.size() == 0);

    int n = 20000;
    // Add a bunch of interval before adding a
    for (int i = 0; i < n; i++) {
      int x = i;
      int y = i+1;
      Interval<Integer> interval = Interval.toInterval(x,y);
      tree.add(interval);
    }
    tree.add(a);
    overlapping1 = tree.getOverlapping(before);
    assertTrue(overlapping1.isEmpty());
    overlapping2 = tree.getOverlapping(included);
    assertTrue(overlapping2.size() == 1);
    overlapping3 = tree.getOverlapping(after);
    assertTrue(overlapping3.isEmpty());
    assertTrue(tree.height() < 20);

    // Try balancing the tree
//    System.out.println("Height is " + tree.height());
    tree.check();
    tree.balance();
    int height = tree.height();
    assertTrue(height < 20);
    tree.check();

    overlapping1 = tree.getOverlapping(before);
    assertTrue(overlapping1.isEmpty());
    overlapping2 = tree.getOverlapping(included);
    assertTrue(overlapping2.size() == 1);
    overlapping3 = tree.getOverlapping(after);
    assertTrue(overlapping3.isEmpty());

    // Clear tree
    tree.clear();
    assertTrue(tree.size() == 0);

    // Add a bunch of random interval before adding a

    Random rand = new Random();
    List<Interval<Integer>> list = new ArrayList<>(n+1);
    for (int i = 0; i < n; i++) {
      int x = rand.nextInt();
      int y = rand.nextInt();
      Interval<Integer> interval = Interval.toValidInterval(x,y);
      tree.add(interval);
      list.add(interval);
    }
    tree.add(a);
    list.add(a);
    overlapping1 = tree.getOverlapping(before);
    checkOverlapping(list, overlapping1, before);

    overlapping2 = tree.getOverlapping(included);
    checkOverlapping(list, overlapping2, included);

    overlapping3 = tree.getOverlapping(after);
    checkOverlapping(list, overlapping3, after);
  }

  public void testIteratorRandom() throws Exception
  {
    int n = 1000;
    IntervalTree<Integer, Interval<Integer>> tree = new IntervalTree<>();

    Random rand = new Random();
    List<Interval<Integer>> list = new ArrayList<>(n+1);
    for (int i = 0; i < n; i++) {
      int x = rand.nextInt();
      int y = rand.nextInt();
      Interval<Integer> interval = Interval.toValidInterval(x,y);
      tree.add(interval);
      list.add(interval);
    }

    Collections.sort(list);

    Interval<Integer> next = null;
    Iterator<Interval<Integer>> iterator = tree.iterator();
    for (int i = 0; i < list.size(); i++) {
      assertTrue("HasItem " + i, iterator.hasNext());
      next = iterator.next();
      assertEquals("Item " + i, list.get(i), next);
    }
    assertFalse("No more items", iterator.hasNext());
  }

  public void testIteratorOrdered() throws Exception
  {
    int n = 1000;
    IntervalTree<Integer, Interval<Integer>> tree = new IntervalTree<>();

    List<Interval<Integer>> list = new ArrayList<>(n+1);
    for (int i = 0; i < n; i++) {
      int x = i;
      int y = i+1;
      Interval<Integer> interval = Interval.toValidInterval(x,y);
      tree.add(interval);
      list.add(interval);
    }

    Collections.sort(list);

    Interval<Integer> next = null;
    Iterator<Interval<Integer>> iterator = tree.iterator();
    for (int i = 0; i < list.size(); i++) {
      assertTrue("HasItem " + i, iterator.hasNext());
      next = iterator.next();
      assertEquals("Item " + i, list.get(i), next);
    }
    assertFalse("No more items", iterator.hasNext());
  }

  public void testSizeOne() {
    IntervalTree<Integer, Interval<Integer>> tree = new IntervalTree<>();
    tree.add(Interval.toInterval(0, 1));
    assertEquals(tree.size(), 1);
    tree.remove(Interval.toInterval(0, 1));
    assertEquals(tree.size(), 0);
  }

  public void testSizeTwoDeleteLeft() {
    IntervalTree<Integer, Interval<Integer>> tree = new IntervalTree<>();
    tree.add(Interval.toInterval(0, 1));
    assertEquals(tree.size(), 1);

    tree.add(Interval.toInterval(2, 5));
    assertEquals(tree.size(), 2);
    assertEquals(tree.root().maxEnd.intValue(), 5);

    tree.remove(Interval.toInterval(0, 1));
    assertEquals(tree.size(), 1);
    assertEquals(tree.root().maxEnd.intValue(), 5);

    // new tree, insert in opposite order
    tree = new IntervalTree<>();
    tree.add(Interval.toInterval(2, 5));
    assertEquals(tree.size(), 1);
    assertEquals(tree.root().maxEnd.intValue(), 5);

    tree.add(Interval.toInterval(0, 1));
    assertEquals(tree.size(), 2);
    assertEquals(tree.root().maxEnd.intValue(), 5);

    tree.remove(Interval.toInterval(0, 1));
    assertEquals(tree.size(), 1);
    assertEquals(tree.root().maxEnd.intValue(), 5);
  }

  public void testSizeTwoDeleteRight() {
    IntervalTree<Integer, Interval<Integer>> tree = new IntervalTree<>();
    tree.add(Interval.toInterval(0, 1));
    assertEquals(tree.size(), 1);
    assertEquals(tree.root().maxEnd.intValue(), 1);

    tree.add(Interval.toInterval(2, 5));
    assertEquals(tree.size(), 2);
    assertEquals(tree.root().maxEnd.intValue(), 5);

    tree.remove(Interval.toInterval(2, 5));
    assertEquals(tree.size(), 1);
    assertEquals(tree.root().maxEnd.intValue(), 1);

    tree.remove(Interval.toInterval(0, 1));
    assertEquals(tree.size(), 0);

    // new tree, insert in opposite order
    tree = new IntervalTree<>();
    tree.add(Interval.toInterval(2, 5));
    assertEquals(tree.size(), 1);
    assertEquals(tree.root().maxEnd.intValue(), 5);

    tree.add(Interval.toInterval(0, 1));
    assertEquals(tree.size(), 2);
    assertEquals(tree.root().maxEnd.intValue(), 5);

    tree.remove(Interval.toInterval(2, 5));
    assertEquals(tree.size(), 1);
    assertEquals(tree.root().maxEnd.intValue(), 1);

    tree.remove(Interval.toInterval(0, 1));
    assertEquals(tree.size(), 0);

  }
}
