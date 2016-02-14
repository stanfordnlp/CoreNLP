package edu.stanford.nlp.util; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.*;

/**
 * Implements a heap as an ArrayList.
 * Values are all implicit in the comparator
 * passed in on construction.  Decrease key is supported, though only
 * lg(n).  Unlike the previous implementation of this class, this
 * heap interprets the addition of an existing element as a "change
 * key" which gets ignored unless it actually turns out to be a
 * decrease key.  Note that in this implementation, changing the key
 * of an object should trigger a change in the comparator's ordering
 * for that object, but should NOT change the equality of that
 * object.
 *
 * @author Dan Klein
 * @author Christopher Manning
 * @version 1.2, 07/31/02
 */
public class ArrayHeap<E> extends AbstractSet<E> implements Heap<E>  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(ArrayHeap.class);

  /**
   * A <code>HeapEntry</code> stores an object in the heap along with
   * its current location (array position) in the heap.
   *
   * @author <a href="mailto:klein@cs.stanford.edu">Dan Klein</a>
   * @version 1.2
   */
  private static final class HeapEntry<E> {
    public E object;
    public int index;
  }

  /**
   * <code>indexToEntry</code> maps linear array locations (not
   * priorities) to heap entries.
   */
  private final ArrayList<HeapEntry<E>> indexToEntry;
  /**
   * <code>objectToEntry</code> maps heap objects to their heap
   * entries.
   */
  private final Map<E,HeapEntry<E>> objectToEntry;
  /**
   * <code>cmp</code> is the comparator passed on construction.
   */
  private final Comparator<? super E> cmp;

  // Primitive Heap Operations

  private static int parent(final int index) {
    return (index - 1) / 2;
  }

  private HeapEntry<E> parent(HeapEntry<E> entry) {
    int index = entry.index;
    return (index > 0 ? indexToEntry.get((index - 1) / 2) : null);
  }

  private HeapEntry<E> leftChild(HeapEntry<E> entry) {
    int index = entry.index;
    int leftIndex = index * 2 + 1;
    return (leftIndex < size() ? indexToEntry.get(leftIndex) : null);
  }

  private HeapEntry<E> rightChild(HeapEntry<E> entry) {
    int index = entry.index;
    int rightIndex = index * 2 + 2;
    return (rightIndex < size() ? indexToEntry.get(rightIndex) : null);
  }

  private int compare(HeapEntry<E> entryA, HeapEntry<E> entryB) {
    return cmp.compare(entryA.object, entryB.object);
  }

  private void swap(HeapEntry<E> entryA, HeapEntry<E> entryB) {
    int indexA = entryA.index;
    int indexB = entryB.index;
    entryA.index = indexB;
    entryB.index = indexA;
    indexToEntry.set(indexA, entryB);
    indexToEntry.set(indexB, entryA);
  }

  /**
   * Remove the last element of the heap (last in the index array).
   * Do not call this on other entries; the last entry is only passed
   * in for efficiency.
   *
   * @param entry the last entry in the array
   */
  private void removeLast(HeapEntry<E> entry) {
    indexToEntry.remove(entry.index);
    objectToEntry.remove(entry.object);
  }

  private HeapEntry<E> getEntry(E o) {
    HeapEntry<E> entry = objectToEntry.get(o);
    if (entry == null) {
      entry = new HeapEntry<>();
      entry.index = size();
      entry.object = o;
      indexToEntry.add(entry);
      objectToEntry.put(o, entry);
    }
    return entry;
  }

  /**
   * iterative heapify up: move item o at index up until correctly placed
   */
  private int heapifyUp(HeapEntry<E> entry) {
    int numSwaps = 0;
    while (true) {
      if (entry.index == 0) {
        break;
      }
      HeapEntry<E> parentEntry = parent(entry);
      if (compare(entry, parentEntry) >= 0) {
        break;
      }
      numSwaps++;
      swap(entry, parentEntry);
    }
    return numSwaps;
  }

  /**
   * On the assumption that
   * leftChild(entry) and rightChild(entry) satisfy the heap property,
   * make sure that the heap at entry satisfies this property by possibly
   * percolating the element o downwards.  I've replaced the obvious
   * recursive formulation with an iterative one to gain (marginal) speed
   */
  private void heapifyDown(HeapEntry<E> entry) {
    // int size = size();

    HeapEntry<E> minEntry; // = null;

    do {
      minEntry = entry;

      HeapEntry<E> leftEntry = leftChild(entry);
      if (leftEntry != null) {
        if (compare(minEntry, leftEntry) > 0) {
          minEntry = leftEntry;
        }
      }

      HeapEntry<E> rightEntry = rightChild(entry);
      if (rightEntry != null) {
        if (compare(minEntry, rightEntry) > 0) {
          minEntry = rightEntry;
        }
      }

      if (minEntry != entry) {
        // Swap min and current
        swap(minEntry, entry);
        // at start of next loop, we set currentIndex to largestIndex
        // this indexation now holds current, so it is unchanged
      }
    } while (minEntry != entry);
    // log.info("Done with heapify down");
    // verify();
  }


  /**
   * Finds the object with the minimum key, removes it from the heap,
   * and returns it.
   *
   * @return The object with minimum key
   */
  @Override
  public E extractMin() {
    if (isEmpty()) {
      throw new NoSuchElementException();
    }
    HeapEntry<E> minEntry = indexToEntry.get(0);
    int lastIndex = size() - 1;
    if (lastIndex > 0) {
      HeapEntry<E> lastEntry =  indexToEntry.get(lastIndex);
      swap(lastEntry, minEntry);
      removeLast(minEntry);
      heapifyDown(lastEntry);
    } else {
      removeLast(minEntry);
    }
    return minEntry.object;
  }

  /**
   * Finds the object with the minimum key and returns it, without
   * modifying the heap.
   *
   * @return The object with minimum key
   */
  @Override
  public E min() {
    HeapEntry<E> minEntry = indexToEntry.get(0);
    return minEntry.object;
  }

  /**
   * Adds an object to the heap.  If the object is already in the heap
   * with worse score, this acts as a decrease key.  If the object is
   * already present, with better score, it will NOT cause an
   * "increase key".
   *
   * @param o an <code>Object</code> value
   */
  @Override
  public boolean add(E o) {
    decreaseKey(o);
    return true;
  }

  /**
   * Changes the position of an element o in the heap based on a
   * change in the ordering of o.  If o's key has actually increased,
   * it will do nothing, particularly not an "increase key".
   *
   * @param o An <code>Object</code> value
   * @return The number of swaps done on decrease.
   */
  @Override
  public int decreaseKey(E o) {
    HeapEntry<E> entry = getEntry(o);
    if (o != entry.object) {
      if (cmp.compare(o, entry.object) < 0) {
        entry.object = o;
      }
    }
    return heapifyUp(entry);
  }

  /**
   * Checks if the heap is empty.
   *
   * @return a <code>boolean</code> value
   */
  @Override
  public boolean isEmpty() {
    return indexToEntry.isEmpty();
  }

  /**
   * Get the number of elements in the heap.
   *
   * @return an <code>int</code> value
   */
  @Override
  public int size() {
    return indexToEntry.size();
  }

  @Override
  public Iterator<E> iterator() {
    Heap<E> tempHeap = new ArrayHeap<>(cmp, size());
    List<E> tempList = new ArrayList<>(size());
    for (E obj : objectToEntry.keySet()) {
      tempHeap.add(obj);
    }
    while (!tempHeap.isEmpty()) {
      tempList.add(tempHeap.extractMin());
    }
    return tempList.iterator();
  }

  /**
   * Clears the heap.  Equivalent to calling extractMin repeatedly
   * (but faster).
   */
  @Override
  public void clear() {
    indexToEntry.clear();
    objectToEntry.clear();
  }

  public void dump() {
    for (int j = 0; j < indexToEntry.size(); j++) {
      log.info(" " + j + " " + ((Scored) indexToEntry.get(j).object).score());
    }
  }

  public void verify() {
    for (int i = 0; i < indexToEntry.size(); i++) {
      if (i != 0) {
        // check ordering
        if (compare(indexToEntry.get(i), indexToEntry.get(parent(i))) < 0) {
          log.info("Error in the ordering of the heap! (" + i + ")");
          dump();
          System.exit(0);
        }
      }
      // check placement
      if (i != indexToEntry.get(i).index) {
        log.info("Error in placement in the heap!");
      }
    }
  }

  /** Create an ArrayHeap.
   *
   *  @param cmp The objects added will be ordered using the <code>Comparator</code>.
   */
  public ArrayHeap(Comparator<? super E> cmp) {
    this.cmp = cmp;
    indexToEntry = new ArrayList<>();
    objectToEntry = Generics.newHashMap();
  }

  public ArrayHeap(Comparator<? super E> cmp, int initCapacity) {
    this.cmp = cmp;
    indexToEntry = new ArrayList<>(initCapacity);
    objectToEntry = Generics.newHashMap(initCapacity);
  }

  public List<E> asList() {
    return new LinkedList<>(this);
  }

  /**
   * Prints the array entries in sorted comparator order.
   * @return The array entries in sorted comparator order.
   */
  @Override
  public String toString() {
    ArrayList<E> result = new ArrayList<>();
    for(E key : objectToEntry.keySet())
      result.add(key);
    Collections.sort(result,cmp);
    return result.toString();
  }

}
