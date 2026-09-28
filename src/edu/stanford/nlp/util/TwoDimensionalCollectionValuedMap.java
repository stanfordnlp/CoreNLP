package edu.stanford.nlp.util;

import java.io.Serializable;
import java.util.*;
import java.util.Map.Entry;

/**
 * A class which can store mappings from Object keys to {@link Collection}s of Object values.
 * Important methods are the {@link #add} and for adding a value
 * to/from the Collection associated with the key, and the {@link #get} method for
 * getting the Collection associated with a key.
 * The class is quite general, because on construction, it is possible to pass a {@link MapFactory}
 * which will be used to create the underlying map and a {@link CollectionFactory} which will
 * be used to create the Collections. Thus this class can be configured to act like a "HashSetValuedMap"
 * or a "ListValuedMap", or even a "HashSetValuedIdentityHashMap". The possibilities are endless!
 * @author Teg Grenager (grenager@cs.stanford.edu)
 * @param <K1> the type of the first keys
 * @param <K2> the type of the second keys
 * @param <V> the type of the values
 */
public class TwoDimensionalCollectionValuedMap<K1, K2, V> implements Serializable {

  private static final long serialVersionUID = 1L;

  /** Maps each first key to the CollectionValuedMap for its second keys. */
  private Map<K1,CollectionValuedMap<K2, V>> map = Generics.newHashMap();
  /** Factory for the map used inside each CollectionValuedMap. */
  protected MapFactory<K2, Collection<V>> mf;
  /** Factory for the value Collections. */
  protected CollectionFactory<V> cf;
  /** Passed to each CollectionValuedMap created; if true, a new Collection is created on each change. */
  private final boolean treatCollectionsAsImmutable;

  /**
   * Creates a new empty TwoDimensionalCollectionValuedMap which uses a HashMap as the
   * underlying Map, and HashSets as the Collections in each mapping. Does not
   * treat Collections as immutable.
   */
  public TwoDimensionalCollectionValuedMap() {
    this(false);
  }


  /**
   * Creates a new empty TwoDimensionalCollectionValuedMap which uses a HashMap as the
   * underlying Map, and HashSets as the Collections in each mapping.
   * <br>
   * @param treatCollectionsAsImmutable whether or not to treat collections as immutable
   */
  public TwoDimensionalCollectionValuedMap(boolean treatCollectionsAsImmutable) {
    this(MapFactory.<K2,Collection<V>>hashMapFactory(), CollectionFactory.<V>hashSetFactory(), treatCollectionsAsImmutable);
  }


  /**
   * Creates a new empty TwoDimensionalCollectionValuedMap which uses a HashMap as the
   * underlying Map.  Does not treat Collections as immutable.
   *
   * @param cf a CollectionFactory which will be used to generate the
   * Collections in each mapping
   */
  public TwoDimensionalCollectionValuedMap(CollectionFactory<V> cf) {
    this(MapFactory.<K2,Collection<V>>hashMapFactory(), cf, false);
  }

  /**
   * Creates a new empty TwoDimensionalCollectionValuedMap.
   * Does not treat Collections as immutable.
   * @param mf a MapFactory which will be used to generate the underlying Map
   * @param cf a CollectionFactory which will be used to generate the Collections in each mapping
   */
  public TwoDimensionalCollectionValuedMap(MapFactory<K2, Collection<V>> mf, CollectionFactory<V> cf) {
    this(mf, cf, false);
  }

  /**
   * Creates a new empty TwoDimensionalCollectionValuedMap.
   * @param mf a MapFactory which will be used to generate the underlying Map
   * @param cf a CollectionFactory which will be used to generate the Collections in each mapping
   * @param treatCollectionsAsImmutable if true, forces this Map to create new a Collection everytime
   * a new value is added to or deleted from the Collection a mapping.
   */
  public TwoDimensionalCollectionValuedMap(MapFactory<K2, Collection<V>> mf, CollectionFactory<V> cf, boolean treatCollectionsAsImmutable) {
    this.mf = mf;
    this.cf = cf;
    this.treatCollectionsAsImmutable = treatCollectionsAsImmutable;
  }

  @Override
  public String toString() {
    return map.toString();
  }
  
  /**
   * Adds all the given mappings, replacing the CollectionValuedMap of any existing first key.
   * The given CollectionValuedMaps are stored directly, not copied.
   *
   * @param toAdd the mappings to add
   */
  public void putAll(Map<K1, CollectionValuedMap<K2, V>> toAdd){
    map.putAll(toAdd);
  }
  
  /**
   * Returns the CollectionValuedMap for the given first key, adding a new empty
   * one to this map if the key is absent.
   *
   * @param key1 the first key
   * @return the CollectionValuedMap mapped to by key1, never null, but may be empty.
   */
  public CollectionValuedMap<K2,V> getCollectionValuedMap(K1 key1) {
    CollectionValuedMap<K2,V> cvm = map.get(key1);
    if (cvm == null) {
      cvm = new CollectionValuedMap<>(mf, cf, treatCollectionsAsImmutable);
      map.put(key1, cvm);
    }
    return cvm;
  }

  /**
   * Returns the Collection mapped to by the two keys.  If {@code key1} is absent,
   * an empty CollectionValuedMap is added for it as a side effect.
   *
   * @param key1 the first key
   * @param key2 the second key
   * @return the Collection of values, never null, but may be empty
   */
  public Collection<V> get(K1 key1, K2 key2) {
    return getCollectionValuedMap(key1).get(key2);
  }
  
  /**
   * Adds the value to the Collection mapped to by the key.
   *
   * @param key1 the first key
   * @param key2 the second key
   * @param value the value to add
   */
  public void add(K1 key1, K2 key2, V value) {
    CollectionValuedMap<K2,V> cvm = map.get(key1);
    if (cvm == null) {
      cvm = new CollectionValuedMap<>(mf, cf, treatCollectionsAsImmutable);
      map.put(key1,cvm);
    }
    cvm.add(key2,value);
  }

  /**
   * Adds a collection of values to the Collection mapped to by the key.
   *
   * @param key1 the first key
   * @param key2 the second key
   * @param value the values to add
   */
  public void add(K1 key1, K2 key2, Collection<V> value) {
    CollectionValuedMap<K2,V> cvm = map.get(key1);
    if (cvm == null) {
      cvm = new CollectionValuedMap<>(mf, cf, treatCollectionsAsImmutable);
      map.put(key1,cvm);
    }
    for(V v: value)
    cvm.add(key2,v);
  }
  
  /**
   * yes, this is a weird method, but i need it.
   * Adds an empty CollectionValuedMap for the given first key if it is absent.
   *
   * @param key1 the first key
   */
  public void addKey(K1 key1) {
    CollectionValuedMap<K2,V> cvm = map.get(key1);
    if (cvm == null) {
      cvm = new CollectionValuedMap<>(mf, cf, treatCollectionsAsImmutable);
      map.put(key1,cvm);
    }
  }

  /** Removes all mappings. */
  public void clear() {
    map.clear();
  }
  
  /**
   * Returns the first keys.
   *
   * @return a Set view of the keys in this Map.
   */
  public Set<K1> keySet() {
    return map.keySet();
  }
  
  /**
   * Returns the mappings from first keys to CollectionValuedMaps.
   *
   * @return a Set view of the entries of the underlying map
   */
  public Set<Entry<K1, CollectionValuedMap<K2, V>>> entrySet() {
    return map.entrySet();
  }

  /**
   * Returns whether the given first key is present.
   *
   * @param key the first key
   * @return true if this map has a mapping for the key
   */
  public boolean containsKey(K1 key) {
    return map.containsKey(key);
  }
  
  /**
   * Removes the mappings for all first keys not in the given set.
   *
   * @param keys the first keys to keep
   */
  public void retainAll(Set<K1> keys) {
    for (K1 key : new LinkedList<>(map.keySet())) {
      if (!keys.contains(key)) {
        map.remove(key);
      }
    }    
  }

  /**
   * Returns the first keys; the same as {@link #keySet}.
   *
   * @return a Set view of the first keys
   */
  public Set<K1> firstKeySet() {
    return keySet();
  }

  /**
   * Returns the second keys used with any first key.
   *
   * @return a new Set of all the second keys
   */
  public Set<K2> secondKeySet() {
    Set<K2> keys = Generics.newHashSet();
    for (K1 k1 : map.keySet()) {
      keys.addAll(getCollectionValuedMap(k1).keySet());
    }
    return keys;
  }

  /**
   * Returns all the values in the map, with duplicates removed.
   *
   * @return a new Set of all the values
   */
  public Collection<V> values() {
    Collection<V> allValues = Generics.newHashSet();
    for (K1 k1 : map.keySet()) {
      Collection<Collection<V>> collectionOfValues = getCollectionValuedMap(k1).values();
      for (Collection<V> values : collectionOfValues) {
        allValues.addAll(values);
      }
    }
    return allValues;
  }
}
