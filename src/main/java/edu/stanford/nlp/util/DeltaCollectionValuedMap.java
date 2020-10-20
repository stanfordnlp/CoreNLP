package edu.stanford.nlp.util;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;


/**
 * Implementation of CollectionValuedMap that appears to store an "original"
 * map and changes to that map. No one currently uses it. See {@link DeltaMap}.
 *
 * @author Teg Grenager (grenager@cs.stanford.edu)
 * @version Jan 14, 2004
 */
public class DeltaCollectionValuedMap<K, V> extends CollectionValuedMap<K, V> {

  private static final long serialVersionUID = 1L;

  private final CollectionValuedMap<K, V> originalMap;
  @SuppressWarnings("serial")
  private final Map<K, Collection<V>> deltaMap;

  private static final Object removedValue = new Object();

  static class SimpleEntry<K, V> implements Map.Entry<K, V> {
    K key;
    V value;

    public SimpleEntry(K key, V value) {
      this.key = key;
      this.value = value;
    }

    public SimpleEntry(Map.Entry<K, V> e) {
      this.key = e.getKey();
      this.value = e.getValue();
    }

    @Override
    public K getKey() {
      return key;
    }

    @Override
    public V getValue() {
      return value;
    }

    @Override
    public V setValue(V value) {
      V oldValue = this.value;
      this.value = value;
      return oldValue;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Map.Entry)) {
        return false;
      }
      Map.Entry e = ErasureUtils.uncheckedCast(o);
      return eq(key, e.getKey()) && eq(value, e.getValue());
    }

    @Override
    public int hashCode() {
      return ((key == null) ? 0 : key.hashCode()) ^ ((value == null) ? 0 : value.hashCode());
    }

    @Override
    public String toString() {
      return key + "=" + value;
    }

    private static boolean eq(Object o1, Object o2) {
      return (o1 == null ? o2 == null : o1.equals(o2));
    }
  }

  @Override
  public Collection<V> get(Object key) {
    // key could be not in original or in deltaMap
    // key could be not in original but in deltaMap
    // key could be in original but removed from deltaMap
    // key could be in original but mapped to something else in deltaMap
    Collection<V> deltaResult = deltaMap.get(key);
    if (deltaResult == null) {
      return originalMap.get(key);
    }
    if (deltaResult == removedValue) {
      return cf.newEmptyCollection();
    }
    return deltaResult;
  }

  // Modification Operations

  @Override
  public Collection<V> put(K key, Collection<V> value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void putAll(Map<? extends K, ? extends Collection<V>> m) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void add(K key, V value) {
    Collection<V> deltaC = deltaMap.get(key);
    if (deltaC == null) {
      deltaC = cf.newCollection();
      Collection<V> originalC = originalMap.get(key);
      if (originalC != null) {
        deltaC.addAll(originalC);
      }
      deltaMap.put(key, deltaC);
    }
    deltaC.add(value);
  }

  /**
   * Adds all of the mappings in m to this CollectionValuedMap.
   * If m is a CollectionValuedMap, it will behave strangely. Use the constructor instead.
   *
   */
  @Override
  public void addAll(Map<K, V> m) {
    for (Map.Entry<K, V> e : m.entrySet()) {
      add(e.getKey(), e.getValue());
    }
  }

  @Override
  public Collection<V> remove(Object key) {
    Collection<V> result = get(key);
    deltaMap.put(ErasureUtils.uncheckedCast(key), ErasureUtils.uncheckedCast(removedValue));
    return result;
  }

  @Override
  public void removeMapping(K key, V value) {
    Collection<V> deltaC = deltaMap.get(key);
    if (deltaC == null) {
      Collection<V> originalC = originalMap.get(key);
      if (originalC != null && originalC.contains(value)) {
        deltaC = cf.newCollection();
        deltaC.addAll(originalC);
        deltaMap.put(key, deltaC);
      }
    }
    if (deltaC != null) {
      deltaC.remove(value);
    }
  }

  @SuppressWarnings("SuspiciousMethodCalls")
  @Override
  public boolean containsKey(Object key) {
    // key could be not in original or in deltaMap
    // key could be not in original but in deltaMap
    // key could be in original but removed from deltaMap
    // key could be in original but mapped to something else in deltaMap
    Object value = deltaMap.get(key);
    if (value == null) {
      return originalMap.containsKey(key);
    }
    return value != removedValue;
  }

  @Override
  public boolean containsValue(Object value) {
    throw new UnsupportedOperationException();
  }

  // Bulk Operations

  /**
   * This is more expensive than normal.
   */
  @Override
  public void clear() {
    // iterate over all keys in originalMap and set them to null in deltaMap
    for (K key : originalMap.keySet()) {
      deltaMap.put(key, ErasureUtils.uncheckedCast(removedValue));
    }
  }

  @Override
  public boolean isEmpty() {
    return size() == 0;
  }

  @Override
  public int size() {
    return entrySet().size();
  }

  @Override
  public Collection<Collection<V>> values() {
    throw new UnsupportedOperationException();
  }

  // Views

  /**
   * This is cheap.
   *
   * @return A set view of the mappings contained in this map.
   */
  @Override
  public Set<Entry<K, Collection<V>>> entrySet() {
    return new AbstractSet<Entry<K, Collection<V>>>() {
      @Override
      public Iterator<Map.Entry<K, Collection<V>>> iterator() {
        Predicate<Entry<K, Collection<V>>> filter1 = e -> ! deltaMap.containsKey(e.getKey());

        Iterator<Map.Entry<K, Collection<V>>> iter1 = new FilteredIterator<>(originalMap.entrySet().iterator(), filter1);

        Predicate<Entry<K, Collection<V>>> filter2 = e -> e.getValue() != removedValue;

        Iterator<Map.Entry<K, Collection<V>>> iter2 = new FilteredIterator<>(deltaMap.entrySet().iterator(), filter2);

        return new ConcatenationIterator<>(iter1, iter2);
      }

      @Override
      public int size() {
        int size = 0;
        for (@SuppressWarnings("unused") Entry<K, Collection<V>> ignored : this) {
          size++;
        }
        return size;
      }
    };
  }

  public DeltaCollectionValuedMap(CollectionValuedMap<K, V> originalMap) {
    super(originalMap.mf, originalMap.cf, originalMap.treatCollectionsAsImmutable);
    this.originalMap = originalMap;
    this.deltaMap = mf.newMap();
  }

}
