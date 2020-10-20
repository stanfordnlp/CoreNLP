package edu.stanford.nlp.util;

import java.io.Serializable;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;


/**
 * A priority queue based on a binary heap.  This implementation trades
 * flexibility for speed: while it is up to 2x faster than {@link
 * BinaryHeapPriorityQueue} and nearly as fast as {@link
 * java.util.PriorityQueue}, it does not support removing or changing the
 * priority of an element.  Also, while {@link #getPriority(Object key)
 * getPriority(Object key)} is supported, performance will be linear, not
 * constant.
 *
 * @author Dan Klein, Bill MacCartney
 */
public class FixedPrioritiesPriorityQueue<E>
  extends AbstractSet<E>
  implements PriorityQueue<E>, Iterator<E>, Serializable, Cloneable {

  private static final long serialVersionUID = 1L;
  private int size;
  private int capacity;
  @SuppressWarnings("serial")
  private List<E> elements;
  private double[] priorities;


  // constructors ----------------------------------------------------------

  public FixedPrioritiesPriorityQueue() {
    this(15);
  }

  public FixedPrioritiesPriorityQueue(int capacity) {
    int legalCapacity = 0;
    while (legalCapacity < capacity) {
      legalCapacity = 2 * legalCapacity + 1;
    }
    grow(legalCapacity);
  }


  // iterator methods ------------------------------------------------------

  /**
   * Returns true if the priority queue is non-empty
   */
  @Override
  public boolean hasNext() {
    return ! isEmpty();
  }

  /**
   * Returns the element in the queue with highest priority, and pops it from
   * the queue.
   */
  @Override
  public E next() throws NoSuchElementException {
    return removeFirst();
  }

  /**
   * Not supported -- next() already removes the head of the queue.
   */
  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }


  // PriorityQueue methods -------------------------------------------------

  /**
   * Adds a key to the queue with the given priority.  If the key is already in
   * the queue, it will be added an additional time, NOT promoted/demoted.
   *
   */
  @Override
  public boolean add(E key, double priority) {
    if (size == capacity) {
      grow(2 * capacity + 1);
    }
    elements.add(key);
    priorities[size] = priority;
    heapifyUp(size);
    size++;
    return true;
  }

  /**
   * Not supported in this implementation.
   */
  @Override
  public boolean changePriority(E key, double priority) {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns the highest-priority element without removing it from the
   * queue.
   */
  @Override
  public E getFirst() {
    if (size() > 0)
      return elements.get(0);
    throw new NoSuchElementException();
  }

  /**
   * Note that this method will be linear (not constant) time in this
   * implementation!  Better not to use it.
   */
  @Override
  public double getPriority(Object key) {
    for (int i = 0, sz = elements.size(); i < sz; i++) {
      if (elements.get(i).equals(key)) {
        return priorities[i];
      }
    }
    throw new NoSuchElementException();
  }

  /**
   * Gets the priority of the highest-priority element of the queue.
   */
  @Override
  public double getPriority() {
    // check empty other way around
    if (size() > 0)
      return priorities[0];
    throw new NoSuchElementException();
  }

  /**
   * Not supported in this implementation.
   */
  @Override
  public boolean relaxPriority(E key, double priority) {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns the highest-priority element and removes it from the queue.
   */
  @Override
  public E removeFirst() throws NoSuchElementException {
    E first = getFirst();
    swap(0, size - 1);
    size--;
    elements.remove(size);
    heapifyDown(0);
    return first;
  }

  @Override
  public List<E> toSortedList() {
    // initialize with size
    List<E> list = new ArrayList<>();
    while (hasNext()) {
      list.add(next());
    }
    return list;
  }


  // Set methods -----------------------------------------------------------

  /**
   * Number of elements in the queue.
   */
  @Override
  public int size() {
    return size;
  }

  @Override
  public void clear() {
    size = 0;
    grow(15);
  }

  @Override
  public Iterator<E> iterator() {
    return Collections.unmodifiableCollection(toSortedList()).iterator();
  }


  // -----------------------------------------------------------------------

  private void grow(int newCapacity) {
    List<E> newElements = new ArrayList<>(newCapacity);
    double[] newPriorities = new double[newCapacity];
    if (size > 0) {
      newElements.addAll(elements);
      System.arraycopy(priorities, 0, newPriorities, 0, priorities.length);
    }
    elements = newElements;
    priorities = newPriorities;
    capacity = newCapacity;
  }

  private static int parent(int loc) {
    return (loc - 1) / 2;
  }

  private static int leftChild(int loc) {
    return 2 * loc + 1;
  }

  private static int rightChild(int loc) {
    return 2 * loc + 2;
  }

  private void heapifyUp(int loc) {
    if (loc == 0) return;
    int parent = parent(loc);
    if (priorities[loc] > priorities[parent]) {
      swap(loc, parent);
      heapifyUp(parent);
    }
  }

  private void heapifyDown(int loc) {
    int max = loc;
    int leftChild = leftChild(loc);
    if (leftChild < size()) {
      double priority = priorities[loc];
      double leftChildPriority = priorities[leftChild];
      if (leftChildPriority > priority)
        max = leftChild;
      int rightChild = rightChild(loc);
      if (rightChild < size()) {
        double rightChildPriority = priorities[rightChild(loc)];
        if (rightChildPriority > priority && rightChildPriority > leftChildPriority)
          max = rightChild;
      }
    }
    if (max == loc)
      return;
    swap(loc, max);
    heapifyDown(max);
  }

  private void swap(int loc1, int loc2) {
    double tempPriority = priorities[loc1];
    E tempElement = elements.get(loc1);
    priorities[loc1] = priorities[loc2];
    elements.set(loc1, elements.get(loc2));
    priorities[loc2] = tempPriority;
    elements.set(loc2, tempElement);
  }


  // -----------------------------------------------------------------------

  /**
   * Returns a representation of the queue in decreasing priority order.
   */
  @Override
  public String toString() {
    return toString(size(), null);
  }

  /** {@inheritDoc} */
  @Override
  public String toString(int maxKeysToPrint) {
    return toString(maxKeysToPrint, "%.3f");
  }

  /**
   * Returns a representation of the queue in decreasing priority order,
   * displaying at most maxKeysToPrint elements.
   *
   */
  public String toString(int maxKeysToPrint, String dblFmt) {
    if (maxKeysToPrint <= 0) maxKeysToPrint = Integer.MAX_VALUE;
    FixedPrioritiesPriorityQueue<E> pq = clone();
    StringBuilder sb = new StringBuilder("[");
    int numKeysPrinted = 0;
    while (numKeysPrinted < maxKeysToPrint && pq.hasNext()) {
      double priority = pq.getPriority();
      E element = pq.next();
      sb.append(element);
      sb.append('=');
      if (dblFmt == null) {
        sb.append(priority);
      } else {
        sb.append(String.format(dblFmt, priority));
      }
      if (numKeysPrinted < size() - 1)
        sb.append(", ");
      numKeysPrinted++;
    }
    if (numKeysPrinted < size()) {
      sb.append("...");
    }
    sb.append(']');
    return sb.toString();
  }


  /**
   * Returns a clone of this priority queue.  Modifications to one will not
   * affect modifications to the other.
   */
  @Override
  public final FixedPrioritiesPriorityQueue<E> clone() {
    FixedPrioritiesPriorityQueue<E> clonePQ;
    try {
      clonePQ = ErasureUtils.uncheckedCast(super.clone());
    } catch (CloneNotSupportedException cnse) {
      throw new AssertionError("Should be able to clone.");
    }
    clonePQ.elements = new ArrayList<>(capacity);
    clonePQ.priorities = new double[capacity];
    if (size() > 0) {
      clonePQ.elements.addAll(elements);
      System.arraycopy(priorities, 0, clonePQ.priorities, 0, size());
    }
    return clonePQ;
  }

}
