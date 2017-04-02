package edu.stanford.nlp.util;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Set;

/**
 * Wrap a TwoDimensionalMap as a TwoDimensionalSet.
 *
 * @author John Bauer
 */
public class TwoDimensionalSet<K1, K2> implements Serializable, Iterable<Pair<K1, K2>> {
  private static final long serialVersionUID = 2L;

  private final TwoDimensionalMap<K1, K2, Boolean> backingMap;

  public TwoDimensionalSet() {
    this(new TwoDimensionalMap<>());
  }

  public TwoDimensionalSet(TwoDimensionalMap<K1, K2, Boolean> backingMap) {
    this.backingMap = backingMap;
  }

  public static <K1, K2> TwoDimensionalSet<K1, K2> treeSet() { 
    return new TwoDimensionalSet<>(TwoDimensionalMap.<K1, K2, Boolean>treeMap());
  }

  public static <K1, K2> TwoDimensionalSet<K1, K2> hashSet() { 
    return new TwoDimensionalSet<>(TwoDimensionalMap.<K1, K2, Boolean>hashMap());
  }

  public boolean add(K1 k1, K2 k2) {
    return (backingMap.put(k1, k2, true) != null);
  }

  public boolean addAll(TwoDimensionalSet<? extends K1, ? extends K2> set) {
    boolean result = false;
    for (Pair<? extends K1, ? extends K2> pair : set) {
      if (add(pair.first, pair.second)) {
        result = true;
      }
    }
    return result;
  }

  /**
   * Adds all the keys in the given TwoDimensionalMap.  Returns true iff at least one key is added.
   */
  public boolean addAllKeys(TwoDimensionalMap<? extends K1, ? extends K2, ?> map) {
    boolean result = false;
    for (TwoDimensionalMap.Entry<? extends K1, ? extends K2, ?> entry : map) {
      if (add(entry.getFirstKey(), entry.getSecondKey())) {
        result = true;
      }
    }
    return result;
  }

  public void clear() {
    backingMap.clear();
  }

  public boolean contains(K1 k1, K2 k2) {
    return backingMap.contains(k1, k2);
  }

  public boolean containsAll(TwoDimensionalSet<? extends K1, ? extends K2> set) {
    for (Pair<? extends K1, ? extends K2> pair : set) {
      if (!contains(pair.first, pair.second)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof TwoDimensionalSet)) {
      return false;
    }
    TwoDimensionalSet<?, ?> other = (TwoDimensionalSet) o;
    return backingMap.equals(other.backingMap);
  }

  @Override
  public int hashCode() {
    return backingMap.hashCode();
  }

  public boolean isEmpty() {
    return backingMap.isEmpty();
  }

  public boolean remove(K1 k1, K2 k2) {
    return backingMap.remove(k1, k2);
  }

  public boolean removeAll(TwoDimensionalSet<? extends K1, ? extends K2> set) {
    boolean removed = false;
    for (Pair<? extends K1, ? extends K2> pair : set) {
      if (remove(pair.first, pair.second)) {
        removed = true;
      }
    }
    return removed;
  }

  public int size() {
    return backingMap.size();
  }

  public Set<K1> firstKeySet() {
    return backingMap.firstKeySet();
  }

  public Set<K2> secondKeySet(K1 k1) {
    return backingMap.getMap(k1).keySet();
  }

  /**
   * Iterate over the map using the iterator and entry inner classes.
   */
  public Iterator<Pair<K1, K2>> iterator() {
    return new TwoDimensionalSetIterator<>(this);
  }

  static class TwoDimensionalSetIterator<K1, K2> implements Iterator<Pair<K1, K2>> {
    Iterator<TwoDimensionalMap.Entry<K1, K2, Boolean>> backingIterator;

    TwoDimensionalSetIterator(TwoDimensionalSet<K1, K2> set) {
      backingIterator = set.backingMap.iterator();
    }

    public boolean hasNext() {
      return backingIterator.hasNext();
    }

    public Pair<K1, K2> next() {
      TwoDimensionalMap.Entry<K1, K2, Boolean> entry = backingIterator.next();
      return Pair.makePair(entry.getFirstKey(), entry.getSecondKey());
    }

    public void remove() {
      backingIterator.remove();
    }
  }
}
