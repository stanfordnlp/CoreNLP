package edu.stanford.nlp.util;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * An array-backed set.
 * @author Roger Levy (rog@stanford.edu)
 */
public class ArraySet<E> extends AbstractSet<E> {

  private ArrayList<E> backer;

  /**
   * Constructs an ArraySet.
   */
  public ArraySet() {
    this(10);
  }

  /**
   * Constructs an ArraySet with specified initial size of backing array.
   * @param initialSize initial size of the backing array.
   */
  public ArraySet(int initialSize) {
    backer = new ArrayList<E>(initialSize);
  }

  /**
   * Constructs an ArraySet with the specified elements.
   * @param elements the elements to be put in the set.
   */
  public ArraySet(E ... elements) {
    this(elements.length);
    for (E element : elements) {
      add(element);
    }
  }

  /**
   * Returns iterator over elements of the set.
   */
  @Override
  public Iterator<E> iterator() {
    return backer.iterator();
  }

  /**
   * Adds element to set.
   * @param e the element to be added.
   * @return <code>false</code> if the set already contained (vis. <code>.equals()</code>) the specified element; <code>true</code> otherwise.
   */
  @Override
  public boolean add(E e) {
    if(backer.contains(e))
      return false;
    else
      return backer.add(e);
  }

  /**
   * Returns size of set.
   * @return number of elements in set.
   */
  @Override
  public int size() {
    return backer.size();
  }
}
