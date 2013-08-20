package edu.stanford.nlp.util;

import java.util.List;
import java.util.AbstractSet;
import java.util.Iterator;

/**
 * A PriorityQueue which will purge itself of the worst items when it contains
 * too many items.
 *
 * @author grenager
 */
public class SizeLimitedPriorityQueue<E>  extends AbstractSet<E> implements PriorityQueue<E> {

  private static int verbose = 0;

  private PriorityQueue<E> queue;
  private int minSize;
  private int maxSize;

  public E removeFirst() {
    return queue.removeFirst();
  }

  public E getFirst() {
    return queue.getFirst();
  }

  public double getPriority(E key) {
    return queue.getPriority(key);
  }

  /**
   * Gets the priority of the highest-priority element of the queue
   * (without modifying the queue).
   */
  public double getPriority() {
    return queue.getPriority();
  }

  public boolean add(E key, double priority) {
    purgeQueueIfNecessary();
    return queue.add(key, priority);
  }

  public boolean changePriority(E e, double priority) {
    return queue.changePriority(e, priority);
  }

  public boolean relaxPriority(E e, double priority) {
    return queue.relaxPriority(e, priority);
  }

  public List<E> toSortedList() {
    return queue.toSortedList();
  }

  private void purgeQueueIfNecessary() {
    if (size() < maxSize) return;
    if (verbose>0) System.err.println("Purging queue...");
    // otherwise keep minSize best elements in the queue.
    PriorityQueue<E> newQueue = new BinaryHeapPriorityQueue<E>();
    for (int i=0; i<minSize; i++) {
      E key = queue.getFirst();
      double priority = queue.getPriority(key);
      queue.removeFirst();
      newQueue.relaxPriority(key, priority);
    }
    queue = newQueue;
  }

  @Override
  public Iterator<E> iterator() {
    return queue.iterator();
  }

  @Override
  public int size() {
    return queue.size();
  }

  @Override
  public boolean add(E e) {
    purgeQueueIfNecessary();
    return queue.add(e);
  }

  /** {@inheritDoc} */
  public String toString(int maxKeysToPrint) {
    return queue.toString(maxKeysToPrint);
  }
  
  public SizeLimitedPriorityQueue(int minSize, int maxSize) {
    if (minSize>=maxSize) throw new RuntimeException("Bad arguments to SizeLimitedPriorityQueue constructor");
    this.queue = new BinaryHeapPriorityQueue<E>();
    this.maxSize = maxSize;
    this.minSize = minSize;
  }

  // for testing
  public static void main(String[] args) {
    verbose = 1;
    PriorityQueue<Integer> queue = new SizeLimitedPriorityQueue<Integer>(50, 100);
    System.err.println("Created queue with minSize=" + 50 + " maxSize=" + 100);
    for (int i=0; i<230; i++) {
      queue.add(i, i/10.0);
      System.err.println("Adding " + i + "=" + i/10.0);
    }
    System.err.println("Final queue: " + queue);
  }

}
