package edu.stanford.nlp.util;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Set;

/**
 * Wrap a TwoDimensionalMap as a TwoDimensionalSet.
 *
 * @param <K1> the type of the first element of each pair
 * @param <K2> the type of the second element of each pair
 * @author John Bauer
 */
public class TwoDimensionalSet<K1, K2> implements Serializable, Iterable<Pair<K1, K2>> {
  private static final long serialVersionUID = 2L;

  /** Maps each pair in the set to {@code true}. */
  private final TwoDimensionalMap<K1, K2, Boolean> backingMap;

  /** Creates an empty set backed by a new {@link TwoDimensionalMap}. */
  public TwoDimensionalSet() {
    this(new TwoDimensionalMap<>());
  }

  /**
   * Creates a set backed by the given map, whose keys are the pairs in the set.
   *
   * @param backingMap the map to use; it is not copied, and every value in it should be {@code true}
   */
  public TwoDimensionalSet(TwoDimensionalMap<K1, K2, Boolean> backingMap) {
    this.backingMap = backingMap;
  }

  /**
   * Creates an empty set backed by TreeMaps.
   *
   * @param <K1> the type of the first element of each pair
   * @param <K2> the type of the second element of each pair
   * @return a new empty set
   */
  public static <K1, K2> TwoDimensionalSet<K1, K2> treeSet() { 
    return new TwoDimensionalSet<>(TwoDimensionalMap.<K1, K2, Boolean>treeMap());
  }

  /**
   * Creates an empty set backed by HashMaps.
   *
   * @param <K1> the type of the first element of each pair
   * @param <K2> the type of the second element of each pair
   * @return a new empty set
   */
  public static <K1, K2> TwoDimensionalSet<K1, K2> hashSet() { 
    return new TwoDimensionalSet<>(TwoDimensionalMap.<K1, K2, Boolean>hashMap());
  }

  /**
   * Adds a pair to the set.
   *
   * @param k1 the first element of the pair
   * @param k2 the second element of the pair
   * @return true if the pair was already in the set (the opposite of {@code Set.add})
   */
  public boolean add(K1 k1, K2 k2) {
    return (backingMap.put(k1, k2, true) != null);
  }

  /**
   * Adds all the pairs in the given set.
   *
   * @param set the pairs to add
   * @return true iff at least one of the pairs was already present, as reported by {@link #add}
   */
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
   * Adds all the keys in the given TwoDimensionalMap.  Returns true iff at
   * least one of the key pairs was already present, as reported by {@link #add}.
   *
   * @param map the map whose pairs of keys to add
   * @return true iff at least one of the key pairs was already present
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

  /** Removes all pairs from the set. */
  public void clear() {
    backingMap.clear();
  }

  /**
   * Returns whether the set contains the given pair.
   *
   * @param k1 the first element of the pair
   * @param k2 the second element of the pair
   * @return whether the pair is in the set
   */
  public boolean contains(K1 k1, K2 k2) {
    return backingMap.contains(k1, k2);
  }

  /**
   * Returns whether the set contains every pair of the given set.
   *
   * @param set the pairs to look for
   * @return whether all the pairs are in this set
   */
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

  /**
   * Returns whether the set has no pairs.
   *
   * @return whether the set is empty
   */
  public boolean isEmpty() {
    return backingMap.isEmpty();
  }

  /**
   * Removes a pair from the set.  The backing map's removed value is
   * unboxed, so this throws a {@code NullPointerException} if the pair is
   * not present.
   *
   * @param k1 the first element of the pair
   * @param k2 the second element of the pair
   * @return true if the pair was removed
   */
  public boolean remove(K1 k1, K2 k2) {
    return backingMap.remove(k1, k2);
  }

  /**
   * Removes all the pairs in the given set, each as by {@link #remove}.
   *
   * @param set the pairs to remove
   * @return true iff at least one pair was removed
   */
  public boolean removeAll(TwoDimensionalSet<? extends K1, ? extends K2> set) {
    boolean removed = false;
    for (Pair<? extends K1, ? extends K2> pair : set) {
      if (remove(pair.first, pair.second)) {
        removed = true;
      }
    }
    return removed;
  }

  /**
   * Returns the number of pairs in the set.
   *
   * @return the number of pairs in the set
   */
  public int size() {
    return backingMap.size();
  }

  /**
   * Returns the first elements of the pairs.  This may include first
   * elements with no remaining pairs.
   *
   * @return the backing map's set of first keys
   */
  public Set<K1> firstKeySet() {
    return backingMap.firstKeySet();
  }

  /**
   * Returns the second elements of the pairs with the given first element.
   * If there are none, an empty map is stored for {@code k1} in the backing map.
   *
   * @param k1 the first element
   * @return the backing map's set of second keys for {@code k1}
   */
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
