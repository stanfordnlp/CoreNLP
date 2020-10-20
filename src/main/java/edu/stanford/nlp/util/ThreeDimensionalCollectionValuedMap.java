package edu.stanford.nlp.util;

import java.io.Serializable;
import java.util.*;

/**
 * A class which can store mappings from Object keys to {@link Collection}s of Object values.
 * Important methods are the {@link #add}  for adding a value
 * to/from the Collection associated with the key, and the {@link #get} method for
 * getting the Collection associated with a key.
 * The class is quite general, because on construction, it is possible to pass a {@link MapFactory}
 * which will be used to create the underlying map and a {@link CollectionFactory} which will
 * be used to create the Collections. Thus this class can be configured to act like a "HashSetValuedMap"
 * or a "ListValuedMap", or even a "HashSetValuedIdentityHashMap". The possibilities are endless!
 * @author Teg Grenager (grenager@cs.stanford.edu)
 */
public class ThreeDimensionalCollectionValuedMap<K1, K2, K3, V> implements Serializable {

  private static final long serialVersionUID = 1L;

  private Map<K1,TwoDimensionalCollectionValuedMap<K2, K3, V>> map = Generics.newHashMap();

  @Override
  public String toString() {
    return map.toString();
  }
  
  /**
   * @return the Collection mapped to by key, never null, but may be empty.
   */
  public TwoDimensionalCollectionValuedMap<K2,K3,V> getTwoDimensionalCollectionValuedMap(K1 key1) {
    TwoDimensionalCollectionValuedMap<K2,K3,V> cvm = map.get(key1);
    if (cvm == null) {
      cvm = new TwoDimensionalCollectionValuedMap<>();
      map.put(key1, cvm);
    }
    return cvm;
  }

  public Collection<V> get(K1 key1, K2 key2, K3 key3) {
    return getTwoDimensionalCollectionValuedMap(key1).getCollectionValuedMap(key2).get(key3);
  }
  
  /**
   * Adds the value to the Collection mapped to by the key.
   *
   */
  public void add(K1 key1, K2 key2, K3 key3, V value) {
    TwoDimensionalCollectionValuedMap<K2,K3,V> cvm = getTwoDimensionalCollectionValuedMap(key1);
    cvm.add(key2,key3,value);
  }

  public void clear() {
    map.clear();
  }
  
  /**
   * @return a Set view of the keys in this Map.
   */
  public Set<K1> keySet() {
    return map.keySet();
  }

  public boolean containsKey(K1 key) {
    return map.containsKey(key);
  }
  

}
