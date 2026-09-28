package edu.stanford.nlp.util;

import java.util.*;
import java.io.Serializable;

/**
 * A map indexed by four keys, stored as a map from the first key to a {@link ThreeDimensionalMap}.
 * Note that the lookup methods create (and store) empty inner maps for keys which are not present.
 *
 * @author jrfinkel
 * @param <K1> The type of the first key
 * @param <K2> The type of the second key
 * @param <K3> The type of the third key
 * @param <K4> The type of the fourth key
 * @param <V> The type of the values
 */
public class FourDimensionalMap <K1, K2, K3, K4, V> implements Serializable {

  private static final long serialVersionUID = 5635664746940978837L;
  /** The map from the first key to the maps over the other keys. */
  Map<K1,ThreeDimensionalMap<K2, K3, K4, V>> map;

  /**
   * Returns the number of distinct first keys (not the number of values).
   *
   * @return The number of first keys
   */
  public int size() {
    return map.size();
  }

  /**
   * Store a value for the given keys.
   *
   * @param key1 The first key
   * @param key2 The second key
   * @param key3 The third key
   * @param key4 The fourth key
   * @param value The value to store
   * @return The value previously stored for these keys, or null if there was none
   */
  public V put (K1 key1, K2 key2, K3 key3, K4 key4, V value) {
    ThreeDimensionalMap<K2, K3, K4, V> m = getThreeDimensionalMap(key1);
    return m.put(key2, key3, key4, value);
  }

  /**
   * Returns the value stored for the given keys.
   *
   * @param key1 The first key
   * @param key2 The second key
   * @param key3 The third key
   * @param key4 The fourth key
   * @return The value stored for these keys, or null if there is none
   */
  public V get (K1 key1, K2 key2, K3 key3, K4 key4) {
    return getThreeDimensionalMap(key1).get(key2, key3, key4);
  }

  /**
   * Remove the value stored for the given keys, if any. The inner maps are not removed.
   *
   * @param key1 The first key
   * @param key2 The second key
   * @param key3 The third key
   * @param key4 The fourth key
   */
  public void remove (K1 key1, K2 key2, K3 key3, K4 key4) {
    get(key1, key2, key3).remove(key4);
  }

  /**
   * Returns the map from fourth keys to values for the given first three keys. If there is no such map, an empty one is created and stored.
   *
   * @param key1 The first key
   * @param key2 The second key
   * @param key3 The third key
   * @return The (live) map from fourth keys to values
   */
  public Map<K4, V> get(K1 key1, K2 key2, K3 key3) {
    return get(key1, key2).get(key3);
  }

  /**
   * Returns the map over the third and fourth keys for the given first two keys. If there is no such map, an empty one is created and stored.
   *
   * @param key1 The first key
   * @param key2 The second key
   * @return The (live) map over the third and fourth keys
   */
  public TwoDimensionalMap<K3, K4, V> get(K1 key1, K2 key2) {
    return get(key1).get(key2);
  }

  /**
   * Returns the map over the other keys for the given first key; the same as {@link #getThreeDimensionalMap}. If there is no such map, an empty one is created and stored.
   *
   * @param key1 The first key
   * @return The (live) map over the second, third and fourth keys
   */
  public ThreeDimensionalMap<K2, K3, K4, V> get(K1 key1) {
    return getThreeDimensionalMap(key1);
  }

  /**
   * Returns the map over the other keys for the given first key. If there is no such map, an empty one is created and stored.
   *
   * @param key1 The first key
   * @return The (live) map over the second, third and fourth keys
   */
  public ThreeDimensionalMap<K2, K3, K4, V> getThreeDimensionalMap(K1 key1) {
    ThreeDimensionalMap<K2, K3, K4, V> m = map.get(key1);
    if (m==null) {
      m = new ThreeDimensionalMap<>();
      map.put(key1, m);
    }
    return m;
  }

  /**
   * Returns all the values in the map, in a new list.
   *
   * @return A list of all the values
   */
  public Collection<V> values() {
    List<V> s = Generics.newArrayList();
    for (ThreeDimensionalMap<K2,K3,K4,V> innerMap : map.values()) {
      s.addAll(innerMap.values());
    }
    return s;
  }
  
  /**
   * Returns the first keys of the map.
   *
   * @return The (live) key set of the underlying map
   */
  public Set<K1> firstKeySet() {
    return map.keySet();
  }

  /**
   * Returns all the second keys used with any first key, in a new set.
   *
   * @return The set of second keys
   */
  public Set<K2> secondKeySet() {
    Set<K2> keys = Generics.newHashSet();
    for (K1 k1 : map.keySet()) {
      keys.addAll(get(k1).firstKeySet());
    }
    return keys;
  }
  
  /**
   * Returns all the third keys used with any first and second keys, in a new set.
   *
   * @return The set of third keys
   */
  public Set<K3> thirdKeySet() {
    Set<K3> keys = Generics.newHashSet();
    for (K1 k1 : map.keySet()) {
      ThreeDimensionalMap<K2,K3,K4,V> m3 = map.get(k1);
      for (K2 k2 : m3.firstKeySet()) {
        keys.addAll(m3.get(k2).firstKeySet());
      }
    }
    return keys;
  }
  
  /**
   * Returns all the fourth keys used with any first, second and third keys, in a new set.
   *
   * @return The set of fourth keys
   */
  public Set<K4> fourthKeySet() {
    Set<K4> keys = Generics.newHashSet();
    for (K1 k1 : map.keySet()) {
      ThreeDimensionalMap<K2,K3,K4,V> m3 = map.get(k1);
      for (K2 k2 : m3.firstKeySet()) {
        TwoDimensionalMap<K3,K4,V> m2 = m3.get(k2);
        for (K3 k3 : m2.firstKeySet()) {
          keys.addAll(m2.get(k3).keySet());
        }
      }
    }
    return keys;
  }
  
  /** Create an empty map. */
  public FourDimensionalMap() {
    this.map = Generics.newHashMap();
  }

  @Override
  public String toString() {
    return map.toString();
  }
  
}
