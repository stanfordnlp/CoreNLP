package edu.stanford.nlp.util;

import java.util.*;
import java.io.Serializable;

/**
 * @author jrfinkel
 */
public class FiveDimensionalMap <K1, K2, K3, K4, K5, V> implements Serializable {

  private static final long serialVersionUID = 1L;
  
  Map<K1,FourDimensionalMap<K2, K3, K4, K5, V>> map;

  public V put (K1 key1, K2 key2, K3 key3, K4 key4, K5 key5, V value) {
    FourDimensionalMap<K2, K3, K4, K5, V> m = getFourDimensionalMap(key1);
    return m.put(key2, key3, key4, key5, value);
  }

  public V get (K1 key1, K2 key2, K3 key3, K4 key4, K5 key5) {
    return getFourDimensionalMap(key1).get(key2, key3, key4, key5);
  }

  public Map<K5, V> get(K1 key1, K2 key2, K3 key3, K4 key4) {
    return get(key1, key2, key3).get(key4);
  }

  public TwoDimensionalMap<K4, K5, V> get(K1 key1, K2 key2, K3 key3) {
    return get(key1, key2).get(key3);
  }

  public ThreeDimensionalMap<K3, K4, K5, V> get(K1 key1, K2 key2) {
    return get(key1).get(key2);
  }

  public FourDimensionalMap<K2, K3, K4, K5, V> get(K1 key1) {
    return getFourDimensionalMap(key1);
  }

  public FourDimensionalMap<K2, K3, K4, K5, V> getFourDimensionalMap(K1 key1) {
    FourDimensionalMap<K2, K3, K4, K5, V> m = map.get(key1);
    if (m==null) {
      m = new FourDimensionalMap<>();
      map.put(key1, m);
    }
    return m;
  }

  public Collection<V> values() {
    List<V> s = Generics.newArrayList();
    for (FourDimensionalMap<K2,K3,K4,K5,V> innerMap : map.values()) {
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
      FourDimensionalMap<K2,K3,K4,K5,V> m4 = map.get(k1);
      for (K2 k2 : m4.firstKeySet()) {
        keys.addAll(m4.get(k2).firstKeySet());
      }
    }
    return keys;
  }
  
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
  
  public FiveDimensionalMap() {
    this.map = Generics.newHashMap();
  }

  @Override
  public String toString() {
    return map.toString();
  }
  
}
