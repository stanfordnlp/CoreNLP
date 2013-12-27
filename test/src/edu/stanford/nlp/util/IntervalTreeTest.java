package edu.stanford.nlp.util;

import junit.framework.TestCase;

import java.util.List;
import java.util.Random;

/**
 * Test for the interval tree
 *
 * @author Angel Chang
 */
public class IntervalTreeTest extends TestCase {

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

    // Add a bunch of interval before adding a
    for (int i = 0; i < 20000; i++) {
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

    // Clear tree
    tree.clear();
    assertTrue(tree.size() == 0);

    // Add a bunch of random interval before adding a
    Random rand = new Random();
    for (int i = 0; i < 20000; i++) {
      int x = rand.nextInt();
      int y = rand.nextInt();
      Interval<Integer> interval = Interval.toInterval(x,y);
      tree.add(interval);
    }
    tree.add(a);
    overlapping1 = tree.getOverlapping(before);
    for (Interval<Integer> interval: overlapping1) {
      assertTrue(interval.overlaps(before));
    }
    overlapping2 = tree.getOverlapping(included);
    assertTrue(overlapping2.size() > overlapping1.size());
    for (Interval<Integer> interval: overlapping2) {
      assertTrue(interval.overlaps(included));
    }

    overlapping3 = tree.getOverlapping(after);
    assertTrue(overlapping2.size() > overlapping3.size());
    for (Interval<Integer> interval: overlapping3) {
      assertTrue(interval.overlaps(after));
    }
  }

}
