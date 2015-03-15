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
 */
public class BoundedPriorityQueue<E> extends TreeSet<E> {

  private int remainingCapacity, initialCapacity;

  public BoundedPriorityQueue(int maxSize) {
    super();
    this.initialCapacity = maxSize;
    this.remainingCapacity = maxSize;
  }

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
