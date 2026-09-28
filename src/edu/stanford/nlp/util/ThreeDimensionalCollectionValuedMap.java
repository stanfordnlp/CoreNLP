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
 *
 * @param <K1> the type of the first key
 * @param <K2> the type of the second key
 * @param <K3> the type of the third key
 * @param <V> the type of the values in each collection
 * @author Teg Grenager (grenager@cs.stanford.edu)
 */
public class ThreeDimensionalCollectionValuedMap<K1, K2, K3, V> implements Serializable {

  private static final long serialVersionUID = 1L;

  /** Maps each first key to the TwoDimensionalCollectionValuedMap for its second and third keys. */
  private Map<K1,TwoDimensionalCollectionValuedMap<K2, K3, V>> map = Generics.newHashMap();

  /** Creates an empty map, backed by a HashMap. */
  public ThreeDimensionalCollectionValuedMap() { }

  @Override
  public String toString() {
    return map.toString();
  }
  
  /**
   * Returns the map of second and third keys to values for the given first
   * key.  If there is none, an empty one is created and stored for the key.
   *
   * @param key1 the first key
   * @return the TwoDimensionalCollectionValuedMap mapped to by key1, never null, but may be empty.
   */
  public TwoDimensionalCollectionValuedMap<K2,K3,V> getTwoDimensionalCollectionValuedMap(K1 key1) {
    TwoDimensionalCollectionValuedMap<K2,K3,V> cvm = map.get(key1);
    if (cvm == null) {
      cvm = new TwoDimensionalCollectionValuedMap<>();
      map.put(key1, cvm);
    }
    return cvm;
  }

  /**
   * Returns the collection of values for the given keys.  Looking up keys
   * which are not present stores empty maps for {@code key1} and
   * {@code key2}, as {@link #getTwoDimensionalCollectionValuedMap} does.
   *
   * @param key1 the first key
   * @param key2 the second key
   * @param key3 the third key
   * @return the collection mapped to by the keys, never null, but may be empty
   */
  public Collection<V> get(K1 key1, K2 key2, K3 key3) {
    return getTwoDimensionalCollectionValuedMap(key1).getCollectionValuedMap(key2).get(key3);
  }
  
  /**
   * Adds the value to the Collection mapped to by the key.
   *
   * @param key1 the first key
   * @param key2 the second key
   * @param key3 the third key
   * @param value the value to add
   */
  public void add(K1 key1, K2 key2, K3 key3, V value) {
    TwoDimensionalCollectionValuedMap<K2,K3,V> cvm = getTwoDimensionalCollectionValuedMap(key1);
    cvm.add(key2,key3,value);
  }

  /** Removes all mappings. */
  public void clear() {
    map.clear();
  }
  
  /**
   * Returns the first keys of the map, including any whose maps are empty.
   *
   * @return a Set view of the keys in this Map.
   */
  public Set<K1> keySet() {
    return map.keySet();
  }

  /**
   * Returns whether there is a map for the given first key.  This is true
   * for any key which has been looked up, even if its map is empty.
   *
   * @param key the first key
   * @return whether {@code key} has a map
   */
  public boolean containsKey(K1 key) {
    return map.containsKey(key);
  }
  

}
