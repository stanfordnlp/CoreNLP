package edu.stanford.nlp.util;

import java.util.*;
import java.util.function.Predicate;

/**
 * A Map which wraps an original Map, and only stores the changes (deltas) from
 * the original Map. This increases Map access time (roughly doubles it) but eliminates
 * Map creation time and decreases memory usage (if you're keeping the original Map in memory
 * anyway).
 * <br>
 * @author Teg Grenager (grenager@cs.stanford.edu)
 * @version Jan 9, 2004 9:19:06 AM
 */
public class DeltaMap<K,V> extends AbstractMap<K,V> {

  private Map<K,V> originalMap;
  private Map<K,V> deltaMap;
  private static Object nullValue = new Object();
  private static Object removedValue = new Object();

  static class SimpleEntry<K,V> implements Map.Entry<K,V> {
    K key;
    V value;

    public SimpleEntry(K key, V value) {
      this.key = key;
      this.value = value;
    }

    public SimpleEntry(Map.Entry<K,V> e) {
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


    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Map.Entry)) {
        return false;
      }
      @SuppressWarnings("unchecked")
      Map.Entry<K,V> e = (Map.Entry<K,V>) o;
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

  /**
   * This is more expensive.
   *
   * @param key key whose presence in this map is to be tested.
   * @return <tt>true</tt> if this map contains a mapping for the specified
   *         key.
   */
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

  /**
   * This may cost twice what it would in the original Map.
   *
   * @param key key whose associated value is to be returned.
   * @return the value to which this map maps the specified key, or
   *         {@code null} if the map contains no mapping for this key.
   */
  @Override
  public V get(Object key) {
    // key could be not in original or in deltaMap
    // key could be not in original but in deltaMap
    // key could be in original but removed from deltaMap
    // key could be in original but mapped to something else in deltaMap
    V deltaResult = deltaMap.get(key);
    if (deltaResult == null) {
      return originalMap.get(key);
    }
    if (deltaResult == nullValue) {
      return null;
    }
    if (deltaResult == removedValue) {
      return null;
    }
    return deltaResult;
  }

  // Modification Operations

  /**
   * This may cost twice what it would in the original Map because we have to find
   * the original value for this key.
   *
   * @param key   key with which the specified value is to be associated.
   * @param value value to be associated with the specified key.
   * @return previous value associated with specified key, or <tt>null</tt>
   *         if there was no mapping for key.  A <tt>null</tt> return can
   *         also indicate that the map previously associated <tt>null</tt>
   *         with the specified key, if the implementation supports
   *         <tt>null</tt> values.
   */
  @Override
  @SuppressWarnings("unchecked")
  public V put(K key, V value) {
    if (value == null) {
      return put(key, (V)nullValue);
    }
    // key could be not in original or in deltaMap
    // key could be not in original but in deltaMap
    // key could be in original but removed from deltaMap
    // key could be in original but mapped to something else in deltaMap
    V result = deltaMap.put(key, value);
    if (result == null) {
      return originalMap.get(key);
    }
    if (result == nullValue) {
      return null;
    }
    if (result == removedValue) {
      return null;
    }
    return result;
  }

  /**
   *
   */
  @Override
  @SuppressWarnings("unchecked")
  public V remove(Object key) {
    // always put it locally
    return put((K)key, (V)removedValue);
  }


  // Bulk Operations

  /**
   * This is more expensive than normal.
   */

  @Override
  @SuppressWarnings("unchecked")
  public void clear() {
    // iterate over all keys in originalMap and set them to null in deltaMap
    for (K key : originalMap.keySet()) {
      deltaMap.put(key, (V)removedValue);
    }
  }


  // Views

  /**
   * This is cheap.
   *
   * @return a set view of the mappings contained in this map.
   */
  @Override
  public Set<Map.Entry<K,V>> entrySet() {
    return new AbstractSet<Map.Entry<K,V>>() {
      @Override
      public Iterator<Map.Entry<K,V>> iterator() {
        Predicate<Entry<K,V>> filter1 = e -> ! deltaMap.containsKey(e.getKey());

        Iterator<Map.Entry<K, V>> iter1 = new FilteredIterator<>(originalMap.entrySet().iterator(), filter1);

        Predicate<Entry<K,V>> filter2 = e -> e.getValue() != removedValue;

        class NullingIterator<KK, VV> implements Iterator<Map.Entry<KK,VV>> {
          private Iterator<Map.Entry<KK, VV>> i;

          private NullingIterator(Iterator<Map.Entry<KK, VV>> i) {
            this.i = i;
          }

          @Override
          public boolean hasNext() {
            return i.hasNext();
          }

          @Override
          public Map.Entry<KK, VV> next() {
            Map.Entry<KK,VV> e = i.next();
            Object o = e.getValue();
            if (o == nullValue) {
              return new SimpleEntry<>(e.getKey(), null);
            }
            return e;
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }

        } // end class NullingIterator

        Iterator<Entry<K, V>> iter2 = new FilteredIterator<>(new NullingIterator<>(deltaMap.entrySet().iterator()), filter2);

        return new ConcatenationIterator<>(iter1, iter2);
      }

      @Override
      public int size() {
        int size = 0;
        for (Entry<K, V> kvEntry : this) {
          ErasureUtils.noop(kvEntry);
          size++;
        }
        return size;
      }
    };
  }


  /**
   * This is very cheap.
   *
   * @param originalMap will serve as the basis for this DeltaMap
   */
  public DeltaMap(Map<K,V> originalMap, MapFactory<K,V> mf) {
    this.originalMap = Collections.unmodifiableMap(originalMap); // unmodifiable for debugging only
    this.deltaMap = mf.newMap();
  }

  @SuppressWarnings("unchecked")
  public DeltaMap(Map<K,V> originalMap) {
    this(originalMap, MapFactory.hashMapFactory());
  }

}
