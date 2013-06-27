package edu.stanford.nlp.util;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Map from keys to {@link Collection}s. Important methods are the {@link #add}
 * and {@link #remove} methods for adding and removing a value to/from the
 * Collection associated with the key, and the {@link #get} method for getting
 * the Collection associated with a key. The class is quite general, because on
 * construction, it is possible to pass a {@link MapFactory} which will be used
 * to create the underlying map and a {@link CollectionFactory} which will be
 * used to create the Collections. Thus this class can be configured to act like
 * a "HashSetValuedMap" or a "ListValuedMap", or even a
 * "HashSetValuedIdentityHashMap". The possibilities are endless!
 * 
 * @param <K>
 *          Key type of map
 * @param <V>
 *          Type of the Collection that is the Map's value
 * @author Teg Grenager (grenager@cs.stanford.edu)
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) - cleanup and filling in
 *         types
 */
public class CollectionValuedMap<K, V> implements Map<K, Collection<V>>, Serializable {

  private static final long serialVersionUID = -9064664153962599076l;
  private Map<K, Collection<V>> map;
  protected CollectionFactory<V> cf;
  private boolean treatCollectionsAsImmutable;
  protected MapFactory<K, Collection<V>> mf;

  /**
   * Replaces current Collection mapped to key with the specified Collection.
   * Use carefully!
   * 
   */
  public Collection<V> put(K key, Collection<V> collection) {
    return map.put(key, collection);
  }

  /**
   * Unsupported. Use {@link #addAll(Map)} instead.
   */
  public void putAll(Map<? extends K, ? extends Collection<V>> m) {
    throw new UnsupportedOperationException();
  }

  /**
   * The empty collection to be returned when a <code>get</code> doesn't find
   * the key. The collection returned should be empty, such as
   * Collections.emptySet, for example.
   */
  private final Collection<V> emptyValue;

  /**
   * @return the Collection mapped to by key, never null, but may be empty.
   */
  public Collection<V> get(Object key) {
    Collection<V> c = map.get(key);
    if (c == null) {
      c = emptyValue;
    }
    return c;
  }

  /**
   * Adds the value to the Collection mapped to by the key.
   * 
   */
  public void add(K key, V value) {
    if (treatCollectionsAsImmutable) {
      Collection<V> newC = cf.newCollection();
      Collection<V> c = map.get(key);
      if (c != null) {
        newC.addAll(c);
      }
      newC.add(value);
      map.put(key, newC); // replacing the old collection
    } else {
      Collection<V> c = map.get(key);
      if (c == null) {
        c = cf.newCollection();
        map.put(key, c);
      }
      c.add(value); // modifying the old collection
    }
  }
  
  /**
   * Adds the values to the Collection mapped to by the key.
   */
  
  public void addAll(K key, Collection<V> values) {
    if (treatCollectionsAsImmutable) {
      Collection<V> newC = cf.newCollection();
      Collection<V> c = map.get(key);
      if (c != null) {
        newC.addAll(c);
      }
      newC.addAll(values);
      map.put(key, newC); // replacing the old collection
    } else {
      Collection<V> c = map.get(key);
      if (c == null) {
        c = cf.newCollection();
        map.put(key, c);
      }
      c.addAll(values); // modifying the old collection
    }
  }

  // Just add the key (empty collection, but key is in the keySet
  public void addKey(K key) {
    Collection<V> c = map.get(key);
    if (c == null) {
      c = cf.newCollection();
      map.put(key, c);
    }
  }

  /**
   * Adds all of the mappings in m to this CollectionValuedMap. If m is a
   * CollectionValuedMap, it will behave strangely. Use the constructor instead.
   * 
   */
  public void addAll(Map<K, V> m) {
    if (m instanceof CollectionValuedMap<?, ?>) {
      throw new UnsupportedOperationException();
    }
    for (Map.Entry<K, V> e : m.entrySet()) {
      add(e.getKey(), e.getValue());
    }
  }

  public void addAll(CollectionValuedMap<K, V> cvm) {
    for (Entry<K, Collection<V>> entry : cvm.entrySet()) {
      K key = entry.getKey();
      Collection<V> currentCollection = get(key);
      Collection<V> newValues = entry.getValue();
      if (treatCollectionsAsImmutable) {
        Collection<V> newCollection = cf.newCollection();
        if (currentCollection != null) {
          newCollection.addAll(currentCollection);
        }
        newCollection.addAll(newValues);
        map.put(key, newCollection); // replacing the old collection
      } else {
        boolean needToAdd = false;
        if (currentCollection == emptyValue) {
          currentCollection = cf.newCollection();
          needToAdd = true;
        }
        currentCollection.addAll(newValues); // modifying the old collection
        if (needToAdd) {
          map.put(key, currentCollection);
        }
      }
    }
  }

  /**
   * Removes the mapping associated with this key from this Map.
   * 
   * @return the Collection mapped to by this key.
   */
  public Collection<V> remove(Object key) {
    return map.remove(key);
  }

  /**
   * removes the mappings associated with the keys from this map
   * @param keys
   */
  public void removeAll(Collection<K> keys) {
    for (K k : keys) {
      remove(k);
    }
  }

  /**
   * Removes the value from the Collection mapped to by this key, leaving the
   * rest of the collection intact.
   * 
   * @param key
   *          the key to the Collection to remove the value from
   * @param value
   *          the value to remove
   */
  public void removeMapping(K key, V value) {
    if (treatCollectionsAsImmutable) {
      Collection<V> c = map.get(key);
      if (c != null) {
        Collection<V> newC = cf.newCollection();
        newC.addAll(c);
        newC.remove(value);
        map.put(key, newC);
      }

    } else {
      Collection<V> c = get(key);
      c.remove(value);
    }
  }

  /**
   * Clears this Map.
   */
  public void clear() {
    map.clear();
  }

  /**
   * @return true iff this key is in this map
   */
  public boolean containsKey(Object key) {
    return map.containsKey(key);
  }

  /**
   * Unsupported.
   */
  public boolean containsValue(Object value) {
    throw new UnsupportedOperationException();
  }

  /**
   * @return true iff this Map has no mappings in it.
   */
  public boolean isEmpty() {
    return map.isEmpty();
  }

  /**
   * Each element of the Set is a Map.Entry object, where getKey() returns the
   * key of the mapping, and getValue() returns the Collection mapped to by the
   * key.
   * 
   * @return a Set view of the mappings contained in this map.
   */
  public Set<Entry<K, Collection<V>>> entrySet() {
    return map.entrySet();
  }

  /**
   * @return a Set view of the keys in this Map.
   */
  public Set<K> keySet() {
    return map.keySet();
  }

  /**
   * The number of keys in this map.
   */
  public int size() {
    return map.size();
  }

  /**
   * @return a collection of the values (really, a collection of values) in this
   *         Map
   */
  public Collection<Collection<V>> values() {
    return map.values();
  }

  public Collection<V> allValues() {
    Collection<V> c = cf.newCollection();
    for (Collection<V> c1 : map.values()) {
      c.addAll(c1);
    }
    return c;
  }

  /**
   * @return true iff o is a CollectionValuedMap, and each key maps to the a
   *         Collection of the same objects in o as it does in this
   *         CollectionValuedMap.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof CollectionValuedMap<?, ?>)) {
      return false;
    }

    CollectionValuedMap<K, V> other = ErasureUtils.uncheckedCast(o);

    if (other.size() != size()) {
      return false;
    }

    try {
      for (Map.Entry<K, Collection<V>> e : entrySet()) {
        K key = e.getKey();
        Collection<V> value = e.getValue();
        if (value == null) {
          if (!(other.get(key) == null && other.containsKey(key))) {
            return false;
          }
        } else {
          if (!value.equals(other.get(key))) {
            return false;
          }
        }
      }
    } catch (ClassCastException unused) {
      return false;
    } catch (NullPointerException unused) {
      return false;
    }

    return true;
  }

  /**
   * @return the hashcode of the underlying Map
   */
  @Override
  public int hashCode() {
    return map.hashCode();
  }

  /**
   * Creates a "delta clone" of this Map, where only the differences are
   * represented.
   */
  public CollectionValuedMap<K, V> deltaClone() {
    CollectionValuedMap<K, V> result = new CollectionValuedMap<K, V>(null, cf, true);
    result.map = new DeltaMap<K, Collection<V>>(this.map);
    return result;
  }

  /**
   * @return a clone of this Map
   */
  @Override
  public CollectionValuedMap<K, V> clone() {
    CollectionValuedMap<K, V> result = new CollectionValuedMap<K, V>(this);
    return result;
  }

  /**
   * @return A String representation of this CollectionValuedMap, with special
   *         machinery to avoid recursion problems
   */
  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append('{');

    Iterator<Entry<K, Collection<V>>> i = entrySet().iterator();
    while (i.hasNext()) {
      Map.Entry<K, Collection<V>> e = i.next();
      K key = e.getKey();
      Collection<V> value = e.getValue();
      buf.append(key == this ? "(this Map)" : key).append('=').append(value == this ? "(this Map)" : value);

      if (i.hasNext()) {
        buf.append(", ");
      }
    }

    buf.append('}');
    return buf.toString();
  }

  /**
   * Creates a new empty CollectionValuedMap.
   * 
   * @param mf
   *          a MapFactory which will be used to generate the underlying Map
   * @param cf
   *          a CollectionFactory which will be used to generate the Collections
   *          in each mapping
   * @param treatCollectionsAsImmutable
   *          if true, forces this Map to create new a Collection everytime a
   *          new value is added to or deleted from the Collection a mapping.
   */
  public CollectionValuedMap(MapFactory<K, Collection<V>> mf, CollectionFactory<V> cf,
      boolean treatCollectionsAsImmutable) {
    this.mf = mf;
    this.cf = cf;
    this.treatCollectionsAsImmutable = treatCollectionsAsImmutable;
    this.emptyValue = cf.newEmptyCollection();
    if (mf != null) {
      map = Collections.synchronizedMap(mf.newMap());
    }
  }

  /**
   * Creates a new CollectionValuedMap with all of the mappings from cvm. Same
   * as {@link #clone()}.
   */
  public CollectionValuedMap(CollectionValuedMap<K, V> cvm) {
    this.mf = cvm.mf;
    this.cf = cvm.cf;
    this.treatCollectionsAsImmutable = cvm.treatCollectionsAsImmutable;
    this.emptyValue = cvm.emptyValue;
    map = Collections.synchronizedMap(mf.newMap());
    for (Map.Entry<K, Collection<V>> entry : cvm.map.entrySet()) {
      K key = entry.getKey();
      Collection<V> c = entry.getValue();
      for (V value : c) {
        add(key, value);
      }
    }
  }

  /**
   * Creates a new empty CollectionValuedMap which uses a HashMap as the
   * underlying Map, and HashSets as the Collections in each mapping. Does not
   * treat Collections as immutable.
   */
  public CollectionValuedMap() {
    this(MapFactory.<K, Collection<V>> hashMapFactory(), CollectionFactory.<V> hashSetFactory(), false);
  }

  /**
   * Creates a new empty CollectionValuedMap which uses a HashMap as the
   * underlying Map. Does not treat Collections as immutable.
   * 
   * @param cf
   *          a CollectionFactory which will be used to generate the Collections
   *          in each mapping
   */
  public CollectionValuedMap(CollectionFactory<V> cf) {
    this(MapFactory.<K, Collection<V>> hashMapFactory(), cf, false);
  }

  /**
   * For testing only.
   * 
   * @param args
   *          from command line
   */
  public static void main(String[] args) {
    CollectionValuedMap<Integer, Integer> originalMap = new CollectionValuedMap<Integer, Integer>();
    /*
        for (int i=0; i<4; i++) {
          for (int j=0; j<4; j++) {
            originalMap.add(new Integer(i), new Integer(j));
          }
        }
        originalMap.remove(new Integer(2));
        System.out.println("Map: ");
        System.out.println(originalMap);
        System.exit(0);
    */
    Random r = new Random();
    for (int i = 0; i < 800; i++) {
      Integer rInt1 = Integer.valueOf(r.nextInt(400));
      Integer rInt2 = Integer.valueOf(r.nextInt(400));
      originalMap.add(rInt1, rInt2);
      System.out.println("Adding " + rInt1 + ' ' + rInt2);
    }
    CollectionValuedMap<Integer, Integer> originalCopyMap = new CollectionValuedMap<Integer, Integer>(originalMap);
    CollectionValuedMap<Integer, Integer> deltaCopyMap = new CollectionValuedMap<Integer, Integer>(originalMap);
    CollectionValuedMap<Integer, Integer> deltaMap = new DeltaCollectionValuedMap<Integer, Integer>(originalMap);
    // now make a lot of changes to deltaMap;
    // add and change some stuff
    for (int i = 0; i < 400; i++) {
      Integer rInt1 = Integer.valueOf(r.nextInt(400));
      Integer rInt2 = Integer.valueOf(r.nextInt(400) + 1000);
      deltaMap.add(rInt1, rInt2);
      deltaCopyMap.add(rInt1, rInt2);
      System.out.println("Adding " + rInt1 + ' ' + rInt2);
    }
    // remove some stuff
    for (int i = 0; i < 400; i++) {
      Integer rInt1 = Integer.valueOf(r.nextInt(1400));
      Integer rInt2 = Integer.valueOf(r.nextInt(1400));
      deltaMap.removeMapping(rInt1, rInt2);
      deltaCopyMap.removeMapping(rInt1, rInt2);
      System.out.println("Removing " + rInt1 + ' ' + rInt2);
    }
    System.out.println("original: " + originalMap);
    System.out.println("copy: " + deltaCopyMap);
    System.out.println("delta: " + deltaMap);

    System.out.println("Original preserved? " + originalCopyMap.equals(originalMap));
    System.out.println("Delta accurate? " + deltaMap.equals(deltaCopyMap));
  }
}
