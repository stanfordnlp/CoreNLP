package edu.stanford.nlp.util;

import java.util.*;
import java.io.Serializable;

/**
 * A map with five levels of keys, implemented as a map from the first key to a
 * {@link FourDimensionalMap}. Inner maps are created on demand by the lookup methods.
 *
 * @author jrfinkel
 *
 * @param <K1> The type of the first key
 * @param <K2> The type of the second key
 * @param <K3> The type of the third key
 * @param <K4> The type of the fourth key
 * @param <K5> The type of the fifth key
 * @param <V> The type of the values
 */
public class FiveDimensionalMap <K1, K2, K3, K4, K5, V> implements Serializable {

  private static final long serialVersionUID = 1L;
  
  /** The top-level map from the first key to the inner four-dimensional maps. */
  Map<K1,FourDimensionalMap<K2, K3, K4, K5, V>> map;

  /**
   * Associates the value with the five keys.
   *
   * @param key1 The first key
   * @param key2 The second key
   * @param key3 The third key
   * @param key4 The fourth key
   * @param key5 The fifth key
   * @param value The value to store
   * @return The previous value for these keys, or null if there was none
   */
  public V put (K1 key1, K2 key2, K3 key3, K4 key4, K5 key5, V value) {
    FourDimensionalMap<K2, K3, K4, K5, V> m = getFourDimensionalMap(key1);
    return m.put(key2, key3, key4, key5, value);
  }

  /**
   * Returns the value stored for the five keys.
   * Creates (and stores) empty inner maps for any missing prefix of the keys.
   *
   * @param key1 The first key
   * @param key2 The second key
   * @param key3 The third key
   * @param key4 The fourth key
   * @param key5 The fifth key
   * @return The value, or null if there is none
   */
  public V get (K1 key1, K2 key2, K3 key3, K4 key4, K5 key5) {
    return getFourDimensionalMap(key1).get(key2, key3, key4, key5);
  }

  /**
   * Returns the map from fifth key to value under the first four keys,
   * creating (and storing) empty inner maps as needed.
   *
   * @param key1 The first key
   * @param key2 The second key
   * @param key3 The third key
   * @param key4 The fourth key
   * @return The inner map (not a copy)
   */
  public Map<K5, V> get(K1 key1, K2 key2, K3 key3, K4 key4) {
    return get(key1, key2, key3).get(key4);
  }

  /**
   * Returns the two-dimensional map under the first three keys,
   * creating (and storing) empty inner maps as needed.
   *
   * @param key1 The first key
   * @param key2 The second key
   * @param key3 The third key
   * @return The inner map (not a copy)
   */
  public TwoDimensionalMap<K4, K5, V> get(K1 key1, K2 key2, K3 key3) {
    return get(key1, key2).get(key3);
  }

  /**
   * Returns the three-dimensional map under the first two keys,
   * creating (and storing) empty inner maps as needed.
   *
   * @param key1 The first key
   * @param key2 The second key
   * @return The inner map (not a copy)
   */
  public ThreeDimensionalMap<K3, K4, K5, V> get(K1 key1, K2 key2) {
    return get(key1).get(key2);
  }

  /**
   * Returns the four-dimensional map under the first key; same as
   * {@link #getFourDimensionalMap}.
   *
   * @param key1 The first key
   * @return The inner map (not a copy)
   */
  public FourDimensionalMap<K2, K3, K4, K5, V> get(K1 key1) {
    return getFourDimensionalMap(key1);
  }

  /**
   * Returns the four-dimensional map under the first key,
   * creating and storing an empty one if there is none.
   *
   * @param key1 The first key
   * @return The inner map (not a copy)
   */
  public FourDimensionalMap<K2, K3, K4, K5, V> getFourDimensionalMap(K1 key1) {
    FourDimensionalMap<K2, K3, K4, K5, V> m = map.get(key1);
    if (m==null) {
      m = new FourDimensionalMap<>();
      map.put(key1, m);
    }
    return m;
  }

  /**
   * Returns all values stored in the map.
   *
   * @return A new list of the values
   */
  public Collection<V> values() {
    List<V> s = Generics.newArrayList();
    for (FourDimensionalMap<K2,K3,K4,K5,V> innerMap : map.values()) {
      s.addAll(innerMap.values());
    }
    return s;
  }
  
  /**
   * Returns the set of first keys.
   *
   * @return The key set of the underlying map (not a copy)
   */
  public Set<K1> firstKeySet() {
    return map.keySet();
  }

  /**
   * Returns every second key used under any first key.
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
   * Returns every third key used under any first and second keys.
   *
   * @return A new set of the third keys
   */
  public Set<K3> thirdKeySet() {
    Set<K3> keys = Generics.newHashSet();
    for (K1 k1 : map.keySet()) {
      FourDimensionalMap<K2,K3,K4,K5,V> m4 = map.get(k1);
      for (K2 k2 : m4.firstKeySet()) {
        keys.addAll(m4.get(k2).firstKeySet());
      }
    }
    return keys;
  }
  
  /**
   * Returns every fourth key used under any first three keys.
   *
   * @return A new set of the fourth keys
   */
  public Set<K4> fourthKeySet() {
    Set<K4> keys = Generics.newHashSet();
    for (K1 k1 : map.keySet()) {
      FourDimensionalMap<K2,K3,K4,K5,V> m4 = map.get(k1);
      for (K2 k2 : m4.firstKeySet()) {
        ThreeDimensionalMap<K3,K4,K5,V> m3 = m4.get(k2);
        for (K3 k3 : m3.firstKeySet()) {
          keys.addAll(m3.get(k3).firstKeySet());
        }
      }
    }
    return keys;
  }

  /**
   * Returns every fifth key used under any first four keys.
   *
   * @return A new set of the fifth keys
   */
  public Set<K5> fifthKeySet() {
    Set<K5> keys = Generics.newHashSet();
    for (K1 k1 : map.keySet()) {
      FourDimensionalMap<K2,K3,K4,K5,V> m4 = map.get(k1);
      for (K2 k2 : m4.firstKeySet()) {
        ThreeDimensionalMap<K3,K4,K5,V> m3 = m4.get(k2);
        for (K3 k3 : m3.firstKeySet()) {
          TwoDimensionalMap<K4,K5,V> m2 = m3.get(k3);
          for (K4 k4 : m2.firstKeySet()) {
            keys.addAll(m2.get(k4).keySet());
          }
        }
      }
    }
    return keys;
  }
  
  /** Creates an empty map. */
  public FiveDimensionalMap() {
    this.map = Generics.newHashMap();
  }

  @Override
  public String toString() {
    return map.toString();
  }
  
}
