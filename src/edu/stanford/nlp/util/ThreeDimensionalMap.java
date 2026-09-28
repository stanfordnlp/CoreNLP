package edu.stanford.nlp.util;

import java.util.*;
import java.io.Serializable;

/**
 * A map from triples of keys to values, stored as a map from the first key
 * to a {@link TwoDimensionalMap}.
 *
 * @author jrfinkel
 * @param <K1> The type of the first key
 * @param <K2> The type of the second key
 * @param <K3> The type of the third key
 * @param <V> The type of the values
 */
public class ThreeDimensionalMap<K1, K2, K3, V> implements Serializable {

  private static final long serialVersionUID = 1L;
  /** The underlying map from first keys to TwoDimensionalMaps. */
  Map<K1, TwoDimensionalMap<K2, K3, V>> map;

  /**
   * Returns the number of (key1, key2, key3) mappings in this map.
   *
   * @return The number of values stored
   */
  public int size() {
    int size = 0;
    for (Map.Entry<K1, TwoDimensionalMap<K2, K3, V>> entry : map.entrySet()) {
      size += entry.getValue().size();
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
    for (Map.Entry<K1, TwoDimensionalMap<K2, K3, V>> entry : map.entrySet()) {
      if (!entry.getValue().isEmpty()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Associates the value with the three keys.
   *
   * @param key1 The first key
   * @param key2 The second key
   * @param key3 The third key
   * @param value The value to store
   * @return The previous value for the three keys, or null if there was none
   */
  public V put(K1 key1, K2 key2, K3 key3, V value) {
    TwoDimensionalMap<K2, K3, V> m = getTwoDimensionalMap(key1);
    return m.put(key2, key3, value);
  }

  /**
   * Returns the value for the three keys.  As a side effect, empty inner maps are
   * added for {@code key1} and {@code key2} if they are not already present.
   *
   * @param key1 The first key
   * @param key2 The second key
   * @param key3 The third key
   * @return The value, or null if there is none
   */
  public V get(K1 key1, K2 key2, K3 key3) {
    return getTwoDimensionalMap(key1).get(key2, key3);
  }

  /**
   * Returns whether there is a mapping for the three keys.  Does not add any entries.
   *
   * @param key1 The first key
   * @param key2 The second key
   * @param key3 The third key
   * @return true if a value is stored for the three keys
   */
  public boolean contains(K1 key1, K2 key2, K3 key3) {
    if (!map.containsKey(key1))
      return false;
    if (!map.get(key1).containsKey(key2))
      return false;
    if (!map.get(key1).get(key2).containsKey(key3))
      return false;
    else
      return true;
  }

  /**
   * Removes the value for the three keys, if any.  As a side effect, empty inner
   * maps are added for {@code key1} and {@code key2} if they are not already present.
   *
   * @param key1 The first key
   * @param key2 The second key
   * @param key3 The third key
   */
  public void remove(K1 key1, K2 key2, K3 key3) {
    get(key1, key2).remove(key3);
  }

  /**
   * Returns the map from third keys to values for the first two keys,
   * creating (and storing) empty inner maps if they are not already present.
   *
   * @param key1 The first key
   * @param key2 The second key
   * @return The live inner map for the two keys
   */
  public Map<K3, V> get(K1 key1, K2 key2) {
    return get(key1).get(key2);
  }

  /**
   * Returns the TwoDimensionalMap for the first key; same as {@link #getTwoDimensionalMap(Object)}.
   *
   * @param key1 The first key
   * @return The live inner map for {@code key1}
   */
  public TwoDimensionalMap<K2, K3, V> get(K1 key1) {
    return getTwoDimensionalMap(key1);
  }

  /**
   * Returns the TwoDimensionalMap for the first key, creating and storing an
   * empty one if it is not already present.
   *
   * @param key1 The first key
   * @return The live inner map for {@code key1}
   */
  public TwoDimensionalMap<K2, K3, V> getTwoDimensionalMap(K1 key1) {
    TwoDimensionalMap<K2, K3, V> m = map.get(key1);
    if (m == null) {
      m = new TwoDimensionalMap<>();
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
    List<V> s = Generics.newArrayList();
    for (TwoDimensionalMap<K2, K3, V> innerMap : map.values()) {
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
      keys.addAll(get(k1).firstKeySet());
    }
    return keys;
  }

  /**
   * Returns the set of third keys used with any first and second keys.
   *
   * @return A new set of the third keys
   */
  public Set<K3> thirdKeySet() {
    Set<K3> keys = Generics.newHashSet();
    for (K1 k1 : map.keySet()) {
      TwoDimensionalMap<K2, K3, V> m = map.get(k1);
      for (K2 k2 : m.firstKeySet()) {
        keys.addAll(m.get(k2).keySet());
      }
    }
    return keys;
  }

  /** Creates an empty map backed by HashMaps. */
  public ThreeDimensionalMap() {
    this.map = Generics.newHashMap();
  }

  @Override
  public String toString() {
    return map.toString();
  }

}
