package edu.stanford.nlp.util;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
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
 * @param <K> Key type of map
 * @param <V> Type of the Collection that is the Map's value
 * @author Teg Grenager (grenager@cs.stanford.edu)
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) - cleanup and filling in
 *         types
 */
public class CollectionValuedMap<K, V> implements Map<K, Collection<V>>, Serializable {

  private static final long serialVersionUID = -9064664153962599076L;

  @SuppressWarnings("serial")
  private final Map<K, Collection<V>> map;
  protected final CollectionFactory<V> cf;
  protected final boolean treatCollectionsAsImmutable;
  protected final MapFactory<K, Collection<V>> mf;

  /**
   * Replaces current Collection mapped to key with the specified Collection.
   * Use carefully!
   */
  @Override
  public Collection<V> put(K key, Collection<V> collection) {
    return map.put(key, collection);
  }

  /**
   * Unsupported. Use {@link #addAll(Map)} instead.
   */
  @Override
  public void putAll(Map<? extends K, ? extends Collection<V>> m) {
    throw new UnsupportedOperationException();
  }

  /**
   * The empty collection to be returned when a {@code get} doesn't find
   * the key. The collection returned should be empty, such as
   * Collections.emptySet, for example.
   */
  @SuppressWarnings("serial")
  private final Collection<V> emptyValue;

  /**
   * @return the Collection mapped to by key, never null, but may be empty.
   */
  @Override
  public Collection<V> get(Object key) {
    Collection<V> c = map.get(key);
    if (c == null) {
      c = emptyValue;
    }
    return c;
  }

  /**
   * Adds the value to the Collection mapped to by the key.
   */
  public void add(K key, V value) {
    if (treatCollectionsAsImmutable) {
      Collection<V> newC;
      Collection<V> c = map.get(key);
      if (c != null) {
        newC = cf.newCollection();
        newC.addAll(c);
        newC.add(value);
      } else {
        newC = cf.newSingletonCollection(value);
      }
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
    if (values.size() == 0) {
      return;
    }
    if (values.size() == 1) {
      add(key, values.iterator().next());
      return;
    }
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

  /** Just add the key (empty collection, but key is in the keySet). */
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
        if (newCollection.size() == 0) {
          newCollection = cf.newEmptyCollection();
        } else if (newCollection.size() == 1) {
          newCollection = cf.newSingletonCollection(newCollection.iterator().next());
        }
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
  @Override
  public Collection<V> remove(Object key) {
    return map.remove(key);
  }

  /**
   * Removes the mappings associated with the keys from this map.
   *
   * @param keys They keys to remove
   */
  @SuppressWarnings("Convert2streamapi")
  public void removeAll(Collection<K> keys) {
    for (K k : keys) {
      remove(k);
    }
  }

  /**
   * Removes the value from the Collection mapped to by this key, leaving the
   * rest of the collection intact.
   *
   * @param key The key to the Collection to remove the value from
   * @param value The value to remove
   */
  public void removeMapping(K key, V value) {
    if (treatCollectionsAsImmutable) {
      Collection<V> c = map.get(key);
      if (c != null) {
        Collection<V> newC = cf.newCollection();
        newC.addAll(c);
        newC.remove(value);
        if (newC.size() == 0) {
          newC = cf.newEmptyCollection();
        } else if (newC.size() == 1) {
          newC = cf.newSingletonCollection(newC.iterator().next());
        }
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
  @Override
  public void clear() {
    map.clear();
  }

  /**
   * @return true iff this key is in this map
   */
  @Override
  public boolean containsKey(Object key) {
    return map.containsKey(key);
  }

  /**
   * Unsupported.
   */
  @Override
  public boolean containsValue(Object value) {
    throw new UnsupportedOperationException();
  }

  /**
   * @return true iff this Map has no mappings in it.
   */
  @Override
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
  @Override
  public Set<Entry<K, Collection<V>>> entrySet() {
    return map.entrySet();
  }

  /**
   * @return a Set view of the keys in this Map.
   */
  @Override
  public Set<K> keySet() {
    return map.keySet();
  }

  /**
   * The number of keys in this map.
   */
  @Override
  public int size() {
    return map.size();
  }

  /**
   * @return a collection of the values (really, a collection of values) in this
   *         Map
   */
  @Override
  public Collection<Collection<V>> values() {
    return map.values();
  }

  @SuppressWarnings("Convert2streamapi")
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
    } catch (ClassCastException | NullPointerException unused) {
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
   * Creates a "delta copy" of this Map, where only the differences
   * from the original Map are represented. (This typically assumes
   * that this map will no longer be changed.)
   */
  public CollectionValuedMap<K, V> deltaCopy() {
    Map<K,Collection<V>> deltaMap = new DeltaMap<>(this.map);
    return new CollectionValuedMap<>(null, cf, true, deltaMap);
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
   * @param mf A MapFactory which will be used to generate the underlying Map
   * @param cf A CollectionFactory which will be used to generate the Collections
   *          in each mapping
   * @param treatCollectionsAsImmutable If true, forces this Map to create new a Collection every time a
   *          new value is added to or deleted from the Collection a mapping.
   */
  public CollectionValuedMap(MapFactory<K, Collection<V>> mf, CollectionFactory<V> cf,
                             boolean treatCollectionsAsImmutable) {
    this(mf, cf, treatCollectionsAsImmutable, null);
  }

  /**
   * Creates a new CollectionValuedMap.
   *
   * @param mf A MapFactory which will be used to generate the underlying Map
   * @param cf A CollectionFactory which will be used to generate the Collections
   *          in each mapping
   * @param treatCollectionsAsImmutable If true, forces this Map to create new a Collection every time a
   *          new value is added to or deleted from the Collection a mapping.
   * @param map An existing map to use rather than initializing one with mf. If this is non-null it is
   *            used to initialize the map rather than mf.
   */
  private CollectionValuedMap(MapFactory<K, Collection<V>> mf, CollectionFactory<V> cf,
                             boolean treatCollectionsAsImmutable,
                             Map<K, Collection<V>> map) {
    if (cf == null) {
      throw new IllegalArgumentException();
    }
    if (mf == null && map == null) {
      throw new IllegalArgumentException();
    }
    this.mf = mf;
    this.cf = cf;
    this.treatCollectionsAsImmutable = treatCollectionsAsImmutable;
    this.emptyValue = cf.newEmptyCollection();
    if (map != null) {
      this.map = map;
    } else {
      this.map = Collections.synchronizedMap(mf.newMap());
    }
  }

  /**
   * Creates a new CollectionValuedMap with all of the mappings from cvm.
   *
   * @param cvm The CollectionValueMap to copy as this object.
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
    this(MapFactory.hashMapFactory(), CollectionFactory.hashSetFactory(), false);
  }

  /**
   * Creates a new empty CollectionValuedMap which uses a HashMap as the
   * underlying Map. Does not treat Collections as immutable.
   *
   * @param cf A CollectionFactory which will be used to generate the Collections
   *          in each mapping
   */
  public CollectionValuedMap(CollectionFactory<V> cf) {
    this(MapFactory.hashMapFactory(), cf, false);
  }

}
