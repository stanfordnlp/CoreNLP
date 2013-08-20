package edu.stanford.nlp.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Collection;
import java.util.Iterator;
import java.io.Serializable;
import java.util.Set;


/**
 * A thin wrapper on a ConcurrentHashMap, turning it into a
 * ConcurrentHashSet.  Such a thing already exists in Java 1.6, using
 * code such as
 * <br>
 * <code>
 * Collections.newSetFromMap(new ConcurrentHashMap&lt;Object,Boolean&gt;())
 * </code>
 * <br>
 * However, this isn't available in Java 1.5, so we do it ourselves.
 * <br>
 * @author John Bauer
 */
public class ConcurrentHashSet<E> implements Set<E>, Serializable {
  private static final long serialVersionUID = 198752987264L;  

  private ConcurrentHashMap<E, Boolean> backingMap = 
    new ConcurrentHashMap<E, Boolean>();

  private static final Boolean TRUE = true;

  public boolean add(E e) {
    Boolean v = backingMap.put(e, TRUE);
    return (v == null);
  }

  public boolean addAll(Collection<? extends E> c) {
    boolean modified = false;
    for (E e : c) {
      if (add(e)) {
        modified = true;
      }
    }
    return modified;
  }

  public void clear() {
    backingMap.clear();
  }

  public boolean contains(Object o) {
    return backingMap.containsKey(o);
  }

  public boolean containsAll(Collection<?> c) {
    return backingMap.keySet().containsAll(c);
  }

  public boolean equals(Object o) {
    return backingMap.keySet().equals(o);
  }

  public int hashCode() {
    return backingMap.hashCode();
  }

  public boolean isEmpty() {
    return backingMap.isEmpty();
  }

  public Iterator<E> iterator() {
    return backingMap.keySet().iterator();
  }

  public boolean remove(Object o) {
    Boolean v = backingMap.remove(o);
    return (v != null);
  }

  public boolean removeAll(Collection<?> c) {
    return backingMap.keySet().removeAll(c);
  }

  public boolean retainAll(Collection<?> c) {
    return backingMap.keySet().retainAll(c);
  }

  public int size() {
    return backingMap.size();
  }

  public Object[] toArray() {
    return backingMap.keySet().toArray();
  }

  public <T> T[] toArray(T[] a) {
    return backingMap.keySet().toArray(a);
  }
}