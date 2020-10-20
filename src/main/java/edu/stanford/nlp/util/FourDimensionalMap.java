package edu.stanford.nlp.util;

import java.util.*;
import java.io.Serializable;

/**
 * @author jrfinkel
 */
public class FourDimensionalMap <K1, K2, K3, K4, V> implements Serializable {

  private static final long serialVersionUID = 5635664746940978837L;
  Map<K1,ThreeDimensionalMap<K2, K3, K4, V>> map;

  public int size() {
    return map.size();
  }

  public V put (K1 key1, K2 key2, K3 key3, K4 key4, V value) {
    ThreeDimensionalMap<K2, K3, K4, V> m = getThreeDimensionalMap(key1);
    return m.put(key2, key3, key4, value);
  }

  public V get (K1 key1, K2 key2, K3 key3, K4 key4) {
    return getThreeDimensionalMap(key1).get(key2, key3, key4);
  }

  public void remove (K1 key1, K2 key2, K3 key3, K4 key4) {
    get(key1, key2, key3).remove(key4);
  }

  public Map<K4, V> get(K1 key1, K2 key2, K3 key3) {
    return get(key1, key2).get(key3);
  }

  public TwoDimensionalMap<K3, K4, V> get(K1 key1, K2 key2) {
    return get(key1).get(key2);
  }

  public ThreeDimensionalMap<K2, K3, K4, V> get(K1 key1) {
    return getThreeDimensionalMap(key1);
  }

  public ThreeDimensionalMap<K2, K3, K4, V> getThreeDimensionalMap(K1 key1) {
    ThreeDimensionalMap<K2, K3, K4, V> m = map.get(key1);
    if (m==null) {
      m = new ThreeDimensionalMap<>();
      map.put(key1, m);
    }
    return m;
  }

  public Collection<V> values() {
    List<V> s = Generics.newArrayList();
    for (ThreeDimensionalMap<K2,K3,K4,V> innerMap : map.values()) {
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
      ThreeDimensionalMap<K2,K3,K4,V> m3 = map.get(k1);
      for (K2 k2 : m3.firstKeySet()) {
        keys.addAll(m3.get(k2).firstKeySet());
      }
    }
    return keys;
  }
  
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
  
  public FourDimensionalMap() {
    this.map = Generics.newHashMap();
  }

  @Override
  public String toString() {
    return map.toString();
  }
  
}
