package edu.stanford.nlp.util;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A map which contains a pointer to another map, and refers all
 * operations to the second map.  The purpose of this class is to
 * provide a parent to classes (like Counter) which extend Map
 * functionality but wish to allow any backing map implementation.
 *
 * @author Dan Klein
 */
public class CompositionMap<K,V> implements Map<K,V>, Serializable {
  private static final long serialVersionUID = 1L;
  private Map<K,V> map;

  public Map<K,V> getMap() {
    return map;
  }

  public Map<K,V> setMap(Map<K,V> map) {
    Map<K,V> oldMap = this.map;
    this.map = map;
    return oldMap;
  }

  public CompositionMap() {
    this(new HashMap<K,V>());
  }

  public CompositionMap(Map<K,V> map) {
    setMap(map);
  }

  // The rest of the code is to forward operations to the backing map

  public int size() {
    return map.size();
  }

  public boolean isEmpty() {
    return map.isEmpty();
  }

  public boolean containsValue(Object value) {
    return map.containsValue(value);
  }

  public boolean containsKey(Object key) {
    return map.containsKey(key);
  }

  public V get(Object key) {
    return map.get(key);
  }

  public V put(K key, V value) {
    return map.put(key, value);
  }

  public V remove(Object key) {
    return map.remove(key);
  }

  public void putAll(Map<? extends K, ? extends V> t) {
    map.putAll(t);
  }

  public void clear() {
    map.clear();
  }

  public Set<K> keySet() {
    return map.keySet();
  }

  public Collection<V> values() {
    return map.values();
  }

  public Set<java.util.Map.Entry<K,V>> entrySet() {
    return map.entrySet();
  }

  @Override
  public boolean equals(Object o) {
    return map.equals(o);
  }

  @Override
  public int hashCode() {
    return map.hashCode();
  }

  @Override
  public String toString() {
    return map.toString();
  }

  public static void main(String[] args) throws Exception {
    Map<Integer,Integer> map = new CompositionMap<Integer,Integer>();
    for (int i = 0; i < 1000000; i++) {
      Integer o = i;
      map.put(o, o);
    }
  }
}
