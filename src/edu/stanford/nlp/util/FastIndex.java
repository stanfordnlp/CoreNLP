package edu.stanford.nlp.util;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.io.*;
import java.util.*;

/**
 * An Index is a collection that maps between an Object vocabulary and a
 * contiguous non-negative integer index series beginning (inclusively) at 0.
 * Similar to HashIndex, but this version is backed by fastutil (hence the
 * name FastIndex).
 *
 * @author <a href="mailto:mgalley@cs.stanford.edu">Michel Galley</a>
 * @version 1.0
 * @see AbstractCollection
 * @since 1.0
 */
public class FastIndex<E> extends AbstractCollection<E> implements Index<E>, RandomAccess {

  ArrayList<E> objects = new ArrayList<E>();
  Map<E,Integer> indexes = new Object2IntOpenHashMap<E>();
  boolean locked;

  /**
   * Clears this Index.
   */
  @Override
  public void clear() {
    objects.clear();
    indexes.clear();
  }

  /**
   * Returns the index of each elem in a List.
   * @param elems The list of items
   * @return An array of indices
   */
  public int[] indices(Collection<E> elems) {
    int[] indices = new int[elems.size()];
    int i = 0;
    for (E elem : elems) {
      indices[i++] = indexOf(elem);
    }
    return indices;
  }

  /**
   * Looks up the objects corresponding to an array of indices, and returns them in a {@link Collection}.
   * This collection is not a copy, but accesses the data structures of the Index.
   * @param indices An array of indices
   * @return a {@link Collection} of the objects corresponding to the indices argument.
   */
  public Collection<E> objects(final int[] indices) {
    return new AbstractList<E>() {
      @Override
      public E get(int index) {
        return objects.get(indices[index]);
      }

      @Override
      public int size() {
        return indices.length;
      }
    };
  }

  /**
   * Returns the number of indexed objects.
   * @return the number of indexed objects.
   */
  @Override
  public int size() {
    return objects.size();
  }

  /**
   * Gets the object whose index is the integer argument.
   * @param i the integer index to be queried for the corresponding argument
   * @return the object whose index is the integer argument.
   */
  public E get(int i) {
    return objects.get(i);
  }

  /**
   * Returns a complete {@link List} of indexed objects, in the order of their indices.  <b>DANGER!</b>
   * The current implementation returns the actual index list, not a defensive copy.  Messing with this List
   * can seriously screw up the state of the Index.  (perhaps this method needs to be eliminated? I don't think it's
   * ever used in ways that we couldn't use the Index itself for directly.  --Roger, 12/29/04)
   * @return a complete {@link List} of indexed objects
   */
  public List<E> objectsList() {
    return objects;
  }

  /**
   * Queries the Index for whether it's locked or not.
   * @return whether or not the Index is locked
   */
  public boolean isLocked() {
    return locked;
  }

  /** Locks the Index.  A locked index cannot have new elements added to it (calls to {@link #add} will
   * leave the Index unchanged and return <code>false</code>).*/
  public void lock() {
    locked = true;
  }

  /** Unlocks the Index.  A locked index cannot have new elements added to it (calls to {@link #add} will
   * leave the Index unchanged and return <code>false</code>).*/
  public void unlock() {
    locked = false;
  }

  /**
   * Returns the integer index of the Object in the Index or -1 if the Object is not already in the Index.
   * @param o the Object whose index is desired.
   * @return the index of the Object argument.  Returns -1 if the object is not in the index.
   */
  public int indexOf(E o) {
    return indexOf(o, false);
  }

  /**
   * Takes an Object and returns the integer index of the Object,
   * perhaps adding it to the index first.
   * Returns -1 if the Object is not in the Index.
   * (Note: indexOf(x, true) is the direct replacement for the number(x)
   * method in the old Numberer class.)
   *
   * @param o the Object whose index is desired.
   * @param add Whether it is okay to add new items to the index
   * @return the index of the Object argument.  Returns -1 if the object is not in the index.
   */
  public int indexOf(E o, boolean add) {
    Integer index = indexes.get(o);
    if (index == null) {
      if (add && !locked) {
        index = indexes.get(o);
        if (index == null) {
          index = objects.size();
          objects.add(o);
          indexes.put(o, index);
        }
      } else {
        return -1;
      }
    }
    return index;
  }

  /**
   * Adds an object to the Index. If it was already in the Index,
   * then nothing is done.  If it is not in the Index, then it is
   * added iff the Index hasn't been locked.
   *
   * @return true if the item was added to the index and false if the
   *         item was already in the index or if the index is locked
   */
  @Override
  public boolean add(E o) {
    Integer index = indexes.get(o);
    if (index == null && ! locked) {
      index = objects.size();
      objects.add(o);
      indexes.put(o, index);
      return true;
    }
    return false;
  }

  /**
   * Checks whether an Object already has an index in the Index
   * @param o the object to be queried.
   * @return true iff there is an index for the queried object.
   */
  @SuppressWarnings({"SuspiciousMethodCalls"})
  @Override
  public boolean contains(Object o) {
    return indexes.containsKey(o);
  }

  /**
   * Creates a new Index.
   */
  public FastIndex() {
    super();
  }

  /**
   * Creates a new Index.
   * @param capacity Initial capacity of Index.
   */
  public FastIndex(int capacity) {
    super();
    objects = new ArrayList<E>(capacity);
    indexes = new Object2IntOpenHashMap<E>(capacity);
  }

  /**
   * Creates a new Index and adds every member of c to it.
   * @param c A collection of objects
   */
  public FastIndex(Collection<? extends E> c) {
    this();
    addAll(c);
  }

  /** Returns a readable version of the Index contents
   *
   *  @return A String showing the full index contents
   */
  @Override
  public String toString() {
    return toString(Integer.MAX_VALUE);
  }

  /** Returns a readable version of at least part of the Index contents.
   *
   *  @param n Show the first <i>n</i> items in the Index
   *  @return A String rshowing some of the index contents
   */
  public String toString(int n) {
    StringBuilder buff = new StringBuilder("[");
    int sz = objects.size();
    if (n > sz) {
      n = sz;
    }
    int i;
    for (i = 0; i < n; i++) {
      E e = objects.get(i);
      buff.append(i).append("=").append(e);
      if (i < (sz-1)) buff.append(",");
    }
    if (i < sz) buff.append("...");
    buff.append("]");
    return buff.toString();
  }

  private static final long serialVersionUID = 5398562825928375261L;

  /**
   * Returns an iterator over the elements of the collection.
   * @return An iterator over the objects indexed
   */
  @Override
  public Iterator<E> iterator() {
    return objects.iterator();
  }

  /**
   * Removes an object from the index, if it exists (otherwise nothing
   * happens).  Note, the indices of other
   * elements will not be changed, so indices will no longer necessarily
   * be contiguous
   * @param o the object to remove
   * @return whether anything was removed
   */
  @Override
  public boolean remove(Object o) {
    Integer oldIndex = indexes.remove(o);
    if (oldIndex == null) {
      return false;
    }
    objects.set(oldIndex, null);
    return true;
  }

  public void saveToWriter(Writer out) throws IOException {
    throw new UnsupportedOperationException();
  }

  public void saveToFilename(String s) {
    throw new UnsupportedOperationException();
  }
}
