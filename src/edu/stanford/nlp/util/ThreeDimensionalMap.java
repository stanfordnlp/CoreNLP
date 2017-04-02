package edu.stanford.nlp.util;

import java.util.*;
import java.io.Serializable;

/**
 * @author jrfinkel
 */
public class ThreeDimensionalMap<K1, K2, K3, V> implements Serializable {

  private static final long serialVersionUID = 1L;
  Map<K1, TwoDimensionalMap<K2, K3, V>> map;

  public int size() {
    int size = 0;
    for (Map.Entry<K1, TwoDimensionalMap<K2, K3, V>> entry : map.entrySet()) {
      size += entry.getValue().size();
    }
    return size;
  }

  public boolean isEmpty() {
    for (Map.Entry<K1, TwoDimensionalMap<K2, K3, V>> entry : map.entrySet()) {
      if (!entry.getValue().isEmpty()) {
        return false;
      }
    }
    return true;
  }

  public V put(K1 key1, K2 key2, K3 key3, V value) {
    TwoDimensionalMap<K2, K3, V> m = getTwoDimensionalMap(key1);
    return m.put(key2, key3, value);
  }

  public V get(K1 key1, K2 key2, K3 key3) {
    return getTwoDimensionalMap(key1).get(key2, key3);
  }

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

  public void remove(K1 key1, K2 key2, K3 key3) {
    get(key1, key2).remove(key3);
  }

  public Map<K3, V> get(K1 key1, K2 key2) {
    return get(key1).get(key2);
  }

  public TwoDimensionalMap<K2, K3, V> get(K1 key1) {
    return getTwoDimensionalMap(key1);
  }

  public TwoDimensionalMap<K2, K3, V> getTwoDimensionalMap(K1 key1) {
    TwoDimensionalMap<K2, K3, V> m = map.get(key1);
    if (m == null) {
      m = new TwoDimensionalMap<>();
      map.put(key1, m);
    }
    return m;
  }

  public Collection<V> values() {
    List<V> s = Generics.newArrayList();
    for (TwoDimensionalMap<K2, K3, V> innerMap : map.values()) {
      s.addAll(innerMap.values());
    }
    return s;
  }

  public Set<K1> firstKeySet() {
    return map.keySet();
  }

  public Set<K2> secondKeySet() {
    Set<K2> keys = Generics.newHashSet();
    for (K1 k1 : map.keySet()) {
      keys.addAll(get(k1).firstKeySet());
    }
    return keys;
  }

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

  public ThreeDimensionalMap() {
    this.map = Generics.newHashMap();
  }

  @Override
  public String toString() {
    return map.toString();
  }

}
