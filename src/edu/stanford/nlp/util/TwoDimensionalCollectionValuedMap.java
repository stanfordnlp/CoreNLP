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
 */
public class TwoDimensionalCollectionValuedMap<K1, K2, V> implements Serializable {

  private static final long serialVersionUID = 1L;

  private Map<K1,CollectionValuedMap<K2, V>> map = Generics.newHashMap();
  protected MapFactory<K2, Collection<V>> mf;
  protected CollectionFactory<V> cf;
  private boolean treatCollectionsAsImmutable;

  /**
   * Creates a new empty TwoDimensionalCollectionValuedMap which uses a HashMap as the
   * underlying Map, and HashSets as the Collections in each mapping. Does not
   * treat Collections as immutable.
   */
  public TwoDimensionalCollectionValuedMap() {
    this(MapFactory.<K2,Collection<V>>hashMapFactory(), CollectionFactory.<V>hashSetFactory(), false);
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
  
  public void putAll(Map<K1, CollectionValuedMap<K2, V>> toAdd){
    map.putAll(toAdd);
  }
  
  /**
   * @return the Collection mapped to by key, never null, but may be empty.
   */
  public CollectionValuedMap<K2,V> getCollectionValuedMap(K1 key1) {
    CollectionValuedMap<K2,V> cvm = map.get(key1);
    if (cvm == null) {
      cvm = new CollectionValuedMap<K2,V>(mf,cf,treatCollectionsAsImmutable);
      map.put(key1, cvm);
    }
    return cvm;
  }

  public Collection<V> get(K1 key1, K2 key2) {
    return getCollectionValuedMap(key1).get(key2);
  }
  
  /**
   * Adds the value to the Collection mapped to by the key.
   *
   */
  public void add(K1 key1, K2 key2, V value) {
    CollectionValuedMap<K2,V> cvm = map.get(key1);
    if (cvm == null) {
      cvm = new CollectionValuedMap<K2,V>(mf,cf,treatCollectionsAsImmutable);
      map.put(key1,cvm);
    }
    cvm.add(key2,value);
  }

  /**
   * Adds a collection of values to the Collection mapped to by the key.
   *
   */
  public void add(K1 key1, K2 key2, Collection<V> value) {
    CollectionValuedMap<K2,V> cvm = map.get(key1);
    if (cvm == null) {
      cvm = new CollectionValuedMap<K2,V>(mf,cf,treatCollectionsAsImmutable);
      map.put(key1,cvm);
    }
    for(V v: value)
    cvm.add(key2,v);
  }
  
  /**
   * yes, this is a weird method, but i need it.
   *
   */
  public void addKey(K1 key1) {
    CollectionValuedMap<K2,V> cvm = map.get(key1);
    if (cvm == null) {
      cvm = new CollectionValuedMap<K2,V>(mf,cf,treatCollectionsAsImmutable);
      map.put(key1,cvm);
    }
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
  
  public Set<Entry<K1, CollectionValuedMap<K2, V>>> entrySet() {
    return map.entrySet();
  }

  public boolean containsKey(K1 key) {
    return map.containsKey(key);
  }
  
  public void retainAll(Set<K1> keys) {
    for (K1 key : new LinkedList<K1>(map.keySet())) {
      if (!keys.contains(key)) {
        map.remove(key);
      }
    }    
  }

  public Set<K1> firstKeySet() {
    return keySet();
  }

  public Set<K2> secondKeySet() {
    Set<K2> keys = Generics.newHashSet();
    for (K1 k1 : map.keySet()) {
      keys.addAll(getCollectionValuedMap(k1).keySet());
    }
    return keys;
  }

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
