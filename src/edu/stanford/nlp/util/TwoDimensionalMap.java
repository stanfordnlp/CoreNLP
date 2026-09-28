package edu.stanford.nlp.util;

import java.util.*;
import java.io.Serializable;
import java.util.function.Function;

import edu.stanford.nlp.util.MapFactory;

/**
 * A map from pairs of keys to values, stored as a map from the first key
 * to maps from the second key to values.
 *
 * @author grenager
 * @param <K1> The type of the first key
 * @param <K2> The type of the second key
 * @param <V> The type of the values
 */
public class TwoDimensionalMap<K1, K2, V> implements Serializable, Iterable<TwoDimensionalMap.Entry<K1, K2, V>> {

  private static final long serialVersionUID = 2L;
  /** Factory for the outer map, from first keys to inner maps. */
  private final MapFactory<K1, Map<K2, V>> mf1;
  /** Factory for the inner maps, from second keys to values. */
  private final MapFactory<K2, V> mf2;
  /** The underlying map from first keys to inner maps. */
  Map<K1, Map<K2, V>> map;
  
  /**
   * Returns the number of (key1, key2) mappings in this map.
   *
   * @return The number of values stored
   */
  public int size() {
    int size = 0;
    for (Map.Entry<K1, Map<K2, V>> entry : map.entrySet()) {
      size += (entry.getValue().size());
    }
    return size;
  }

  /**
   * Returns whether this map holds no values.  A map containing only
   * empty inner maps is considered empty.
   *
   * @return true if no values are stored
   */
  public boolean isEmpty() {
    for (Map.Entry<K1, Map<K2, V>> entry : map.entrySet()) {
      if (!entry.getValue().isEmpty()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Associates the value with the two keys.
   *
   * @param key1 The first key
   * @param key2 The second key
   * @param value The value to store
   * @return The previous value for the two keys, or null if there was none
   */
  public V put(K1 key1, K2 key2, V value) {
    Map<K2, V> m = getMap(key1);
    return m.put(key2, value);
  }

  // adds empty hashmap for key key1
  /**
   * Adds an empty inner map (made by the inner map factory) for the first key,
   * replacing any existing inner map for it.
   *
   * @param key1 The first key
   */
  public void put(K1 key1) {
    map.put(key1, mf2.newMap());
  }

  /**
   * Returns whether there is a mapping for the two keys.  Does not add any entries.
   *
   * @param key1 The first key
   * @param key2 The second key
   * @return true if a value is stored for the two keys
   */
  public boolean contains(K1 key1, K2 key2) {
    if (!containsKey(key1)) {
      return false;
    }
    return getMap(key1).containsKey(key2);
  }

  /**
   * Returns the value for the two keys.  As a side effect, an empty inner map is
   * added for {@code key1} if it is not already present.
   *
   * @param key1 The first key
   * @param key2 The second key
   * @return The value, or null if there is none
   */
  public V get(K1 key1, K2 key2) {
    Map<K2, V> m = getMap(key1);
    return m.get(key2);
  }

  /**
   * Removes the value for the two keys, if any.  As a side effect, an empty inner
   * map is added for {@code key1} if it is not already present.
   *
   * @param key1 The first key
   * @param key2 The second key
   * @return The value removed, or null if there was none
   */
  public V remove(K1 key1, K2 key2) {
    return get(key1).remove(key2);
  }

  /**
   * Removes all of the data associated with the first key in the map
   *
   * @param key1 The first key
   */
  public void remove(K1 key1) {
    map.remove(key1);
  }

  /** Removes all of the entries in the map. */
  public void clear() {
    map.clear();
  }

  /**
   * Returns whether there is an inner map for the first key (which may be empty).
   *
   * @param key1 The first key
   * @return true if {@code key1} is in the first key set
   */
  public boolean containsKey(K1 key1) {
    return map.containsKey(key1);
  }

  /**
   * Returns the inner map for the first key; same as {@link #getMap(Object)}.
   *
   * @param key1 The first key
   * @return The live inner map for {@code key1}
   */
  public Map<K2, V> get(K1 key1) {
    return getMap(key1);
  }

  /**
   * Returns the inner map for the first key, creating and storing an empty one
   * if it is not already present.
   *
   * @param key1 The first key
   * @return The live inner map for {@code key1}
   */
  public Map<K2, V> getMap(K1 key1) {
    Map<K2, V> m = map.get(key1);
    if (m == null) {
      m = mf2.newMap();
      map.put(key1, m);
    }
    return m;
  }

  /**
   * Returns all of the values in this map.
   *
   * @return A new list of the values
   */
  public Collection<V> values() {
    // TODO: Should return a specialized class
    List<V> s = Generics.newArrayList();
    for (Map<K2, V> innerMap : map.values()) {
      s.addAll(innerMap.values());
    }
    return s;
  }

  /**
   * Returns the set of first keys, including those whose inner maps are empty.
   *
   * @return The live key set of the underlying map
   */
  public Set<K1> firstKeySet() {
    return map.keySet();
  }

  /**
   * Returns the set of second keys used with any first key.
   *
   * @return A new set of the second keys
   */
  public Set<K2> secondKeySet() {
    Set<K2> keys = Generics.newHashSet();
    for (K1 k1 : map.keySet()) {
      keys.addAll(get(k1).keySet());
    }
    return keys;
  }

  /**
   * Adds all of the entries in the <code>other</code> map, performing
   * <code>function</code> on them to transform the values
   *
   * @param <V2> The type of the values in {@code other}
   * @param other The map whose entries to add
   * @param function The function applied to each value of {@code other}
   */
  public <V2> void addAll(TwoDimensionalMap<? extends K1, ? extends K2, ? extends V2> other, Function<V2, V> function) {
    for (TwoDimensionalMap.Entry<? extends K1, ? extends K2, ? extends V2> entry : other) {
      put(entry.getFirstKey(), entry.getSecondKey(), function.apply(entry.getValue()));
    }
  }

  /**
   * Transforms this map into a new map using the given transform function. <br>
   * Assumes that the map factory which produced &lt;K1, K2, V&gt; maps will
   * happily produce &lt;K1, K2, V2&gt; maps.  If that is not true, then
   * this will fail horribly, hopefully right away.
   *
   * @param <V2> The type of the values in the new map
   * @param function The function applied to each value
   * @return A new map with the same keys and the transformed values
   */
  public <V2> TwoDimensionalMap<K1, K2, V2> transform(Function<V, V2> function) {
    MapFactory<K1, Map<K2, V2>> newMF1 = ErasureUtils.uncheckedCast(mf1);
    MapFactory<K2, V2> newMF2 = ErasureUtils.uncheckedCast(mf2);
    TwoDimensionalMap<K1, K2, V2> newMap = new TwoDimensionalMap<K1, K2, V2>(newMF1, newMF2);
    newMap.addAll(this, function);
    return newMap;
  }

  /**
   * Replace each of the elements with the application of a function.
   *
   * TODO: use a TriFunction?  Such a thing does not exist
   *
   * @param f The function applied to each value, whose result replaces it
   */
  public void replaceAll(Function<V, ? extends V> f) {
    for (K1 k : map.keySet()) {
      map.get(k).replaceAll((x, y) -> f.apply(y));
    }
  }

  /** Creates an empty map backed by HashMaps. */
  public TwoDimensionalMap() {
    this(MapFactory.<K1, Map<K2, V>>hashMapFactory(), MapFactory.<K2, V>hashMapFactory());
  }

  /**
   * Creates a copy of the given map, using the same map factories.
   * The inner maps are copied; the keys and values themselves are not.
   *
   * @param tdm The map to copy
   */
  public TwoDimensionalMap(TwoDimensionalMap<K1, K2, V> tdm) {
    this(tdm.mf1, tdm.mf2);
    for (K1 k1 : tdm.map.keySet()) {
      Map<K2, V> m = tdm.map.get(k1);
      Map<K2, V> copy = mf2.newMap();
      copy.putAll(m);
      this.map.put(k1, copy);
    }
  }

  /**
   * Creates an empty map using the given map factories.
   *
   * @param mf1 Factory for the outer map, from first keys to inner maps
   * @param mf2 Factory for the inner maps, from second keys to values
   */
  public TwoDimensionalMap(MapFactory<K1, Map<K2, V>> mf1, MapFactory<K2, V> mf2) {
    this.mf1 = mf1;
    this.mf2 = mf2;
    this.map = mf1.newMap();
  }

  /**
   * Creates an empty map backed by HashMaps.
   *
   * @param <K1> The type of the first key
   * @param <K2> The type of the second key
   * @param <V> The type of the values
   * @return A new empty map
   */
  public static <K1, K2, V> TwoDimensionalMap<K1, K2, V> hashMap() {
    return new TwoDimensionalMap<>(MapFactory.<K1, Map<K2, V>>hashMapFactory(), MapFactory.<K2, V>hashMapFactory());
  }

  /**
   * Creates an empty map backed by TreeMaps.
   *
   * @param <K1> The type of the first key
   * @param <K2> The type of the second key
   * @param <V> The type of the values
   * @return A new empty map
   */
  public static <K1, K2, V> TwoDimensionalMap<K1, K2, V> treeMap() {
    return new TwoDimensionalMap<>(MapFactory.<K1, Map<K2, V>>treeMapFactory(), MapFactory.<K2, V>treeMapFactory());
  }

  /**
   * Creates an empty map backed by IdentityHashMaps.
   *
   * @param <K1> The type of the first key
   * @param <K2> The type of the second key
   * @param <V> The type of the values
   * @return A new empty map
   */
  public static <K1, K2, V> TwoDimensionalMap<K1, K2, V> identityHashMap() {
    return new TwoDimensionalMap<>(MapFactory.<K1, Map<K2, V>>identityHashMapFactory(), MapFactory.<K2, V>identityHashMapFactory());
  }

  @Override
  public String toString() {
    return map.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof TwoDimensionalMap)) {
      return false;
    }
    TwoDimensionalMap<?, ?, ?> other = (TwoDimensionalMap<?, ?, ?>) o;
    return map.equals(other.map);
  }

  @Override
  public int hashCode() {
    return map.hashCode();
  }

  /**
   * Iterate over the map using the iterator and entry inner classes.
   */
  public Iterator<Entry<K1, K2, V>> iterator() {
    return new TwoDimensionalMapIterator<>(this);
  }

  /**
   * Returns an iterator over the values of the map.  Its {@code remove} method
   * is not supported.
   *
   * @return An iterator over the values
   */
  public Iterator<V> valueIterator() {
    return new TwoDimensionalMapValueIterator<>(this);
  }

  static class TwoDimensionalMapValueIterator<K1, K2, V> implements Iterator<V> {
    Iterator<Entry<K1, K2, V>> entryIterator;

    TwoDimensionalMapValueIterator(TwoDimensionalMap<K1, K2, V> map) {
      entryIterator = map.iterator();
    }

    public boolean hasNext() {
      return entryIterator.hasNext();
    }

    public V next() {
      Entry<K1, K2, V> next = entryIterator.next();
      return next.getValue();
    }

    public void remove() {
      entryIterator.remove();
    }
  }

  /**
   * This inner class represents a single entry in the TwoDimensionalMap.  
   * Iterating over the map will give you these.
   *
   * @param <K1> The type of the first key
   * @param <K2> The type of the second key
   * @param <V> The type of the value
   */
  public static class Entry<K1, K2, V> {
    K1 firstKey;
    K2 secondKey;
    V value;

    Entry(K1 k1, K2 k2, V v) { 
      firstKey = k1;
      secondKey = k2;
      value = v;
    }

    /**
     * Returns the first key.
     *
     * @return The first key
     */
    public K1 getFirstKey() { return firstKey; }
    /**
     * Returns the second key.
     *
     * @return The second key
     */
    public K2 getSecondKey() { return secondKey; }
    /**
     * Returns the value.
     *
     * @return The value
     */
    public V getValue() { return value; }

    @Override
    public String toString() {
      return "(" + firstKey + "," + secondKey + "," + value + ")";
    }
  }

  /**
   * Internal class which represents an iterator over the data in the
   * TwoDimensionalMap.  It keeps state in the form of an iterator
   * over the outer map, which maps keys to inner maps, and an
   * iterator over the most recent inner map seen.  When the inner map
   * has been completely iterated over, the outer map iterator
   * advances one step.  The iterator is finished when all key pairs
   * have been returned once.
   */
  static class TwoDimensionalMapIterator<K1, K2, V> implements Iterator<Entry<K1, K2, V>> {
    Iterator<Map.Entry<K1, Map<K2, V>>> outerIterator;
    Iterator<Map.Entry<K2, V>> innerIterator;
    Entry<K1, K2, V> next;

    TwoDimensionalMapIterator(TwoDimensionalMap<K1, K2, V> map) {
      outerIterator = map.map.entrySet().iterator();
      primeNext();
    }

    public boolean hasNext() {
      return next != null;
    }

    public Entry<K1, K2, V> next() {
      if (next == null) {
        throw new NoSuchElementException();
      }
      Entry<K1, K2, V> result = next;
      primeNext();
      return result;
    }

    private void primeNext() {
      K1 k1 = null;
      if (next != null) {
        k1 = next.getFirstKey();
      }
      while (innerIterator == null || !innerIterator.hasNext()) {
        if (!outerIterator.hasNext()) {
          next = null;
          return;
        }
        Map.Entry<K1, Map<K2, V>> outerEntry = outerIterator.next();
        k1 = outerEntry.getKey();
        innerIterator = outerEntry.getValue().entrySet().iterator();
      }
      Map.Entry<K2, V> innerEntry = innerIterator.next();
      next = new Entry<>(k1, innerEntry.getKey(), innerEntry.getValue());
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}
