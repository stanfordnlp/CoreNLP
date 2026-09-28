package edu.stanford.nlp.util;

import java.util.*;

/**
 * A priority queue that has a fixed bounded size.
 * Notice that this class is implemented using a sorted set, which
 * requires consistency between euqals() and compareTo() method.
 * It decides whether two objects are equal based on their compareTo
 * value; in other words, if two objects have the same priority, 
 * only one will be stored.
 *
 * @author Mengqiu Wang
 * @param <E> The type of the elements in the queue
 */
public class BoundedPriorityQueue<E> extends TreeSet<E> {

  /** How many more elements can be added before the queue is full, and the maximum size of the queue. */
  private int remainingCapacity, initialCapacity;

  /**
   * Create an empty queue which orders its elements by their natural ordering.
   * Note that {@link #add} uses the comparator once the queue is full, so it
   * throws a NullPointerException at that point when there is no comparator.
   *
   * @param maxSize The maximum number of elements the queue holds
   */
  public BoundedPriorityQueue(int maxSize) {
    super();
    this.initialCapacity = maxSize;
    this.remainingCapacity = maxSize;
  }

  /**
   * Create an empty queue which orders its elements with the given comparator.
   *
   * @param maxSize The maximum number of elements the queue holds
   * @param comparator The comparator used to order (and deduplicate) elements
   */
  public BoundedPriorityQueue(int maxSize, Comparator<E> comparator) {
    super(comparator);
    this.initialCapacity = maxSize;
    this.remainingCapacity = maxSize;
  }

  @Override
  public void clear() {
    super.clear();
    remainingCapacity = initialCapacity;
  }

  /**
   * @return true if element was successfully added, false otherwise
   * */
  @Override
  public boolean add(E e) {
    if (remainingCapacity == 0 && size() == 0) {
      return false;
    } else if (remainingCapacity > 0) {
      // still has room, add element 
      boolean added = super.add(e);
      if (added) {
          remainingCapacity--;
      }
      return added;
    } else {
      // compare new element with least element in queue
      int compared = super.comparator().compare(e, this.first());
      if (compared == 1) {
        // new element is larger, replace old element 
        pollFirst();
        super.add(e);
        return true;
      } else {
        // new element is smaller, discard
        return false;
      }
    }
  }
}
