package edu.stanford.nlp.util;

import java.lang.ref.SoftReference;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Provides a cache where both keys and values are only weakly referenced
 * allowing garbage collection of either at any time.  Loosely based on
 * {@link java.util.Map}.
 * 
 * @author dramage
 */
public class SoftCache<K,V> extends AbstractMap<K,V> {
  /** cache of values */
  private final Map<K,SoftReference<V>> map
  = new WeakHashMap<K, SoftReference<V>>();

  /**
   * Clears this map.
   */
  @Override
  public void clear() {
    map.clear();
  }

  /**
   * Returns true if a non-null value is associated with the given key
   * (and has not yet been garbage collected).
   */
  @Override
  public boolean containsKey(Object key) {
    SoftReference<V> ref = map.get(key);
    return ref != null && ref.get() != null;
  }

  /**
   * Returns true if the given non-null value is present in the map.
   */
  @Override
  public boolean containsValue(Object value) {
    if (value == null) {
      throw new RuntimeException("SoftCache does not support null values");
    }

    for (SoftReference<V> valRef : map.values()) {
      if (valRef.get() != null && value.equals(valRef.get())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the value currently associated with the given key if one
   * has been set with put and not been subsequently garbage collected.
   */
  @Override
  public V get(Object key) {
    SoftReference<V> valRef = map.get(key);
    return valRef == null ? null : valRef.get();
  }

  /**
   * Returns true if the map is empty.  Note that some values may have
   * been garbage collected resulting in effectively empty maps still
   * returning true.
   */
  @Override
  public boolean isEmpty() {
    return map.isEmpty();
  }

  /**
   * Returns the set of keys currently in this map.
   */
  @Override
  public Set<K> keySet() {
    return map.keySet();
  }

  /**
   * Associates the given key with a weak reference to the given value.
   * Either key or value or both may be garbage collected at any point.
   * Returns the previously associated value or null if none was
   * associated. Value must be non-null.
   */
  @Override
  public V put(K key, V value) {
    if (value == null) {
      throw new IllegalArgumentException("WeakCache does not support null" +
      "values");
    }

    SoftReference<V> valRef = map.put(key, new SoftReference<V>(value));
    return valRef == null ? null : valRef.get();
  }

  /**
   * Removes the given key from the map.
   */
  @Override
  public V remove(Object key) {
    SoftReference<V> valRef = map.remove(key);
    return valRef == null ? null : valRef.get();
  }

  /**
   * Returns the expected size of the cache.  Note that this may over-report
   * as objects may have been garbage collected.
   */
  @Override
  public int size() {
    return map.size();
  }

  /**
   * Returns an immutable set of entries backed by this cache.  Attempts
   * to modify or remove values in this map will fail.
   */
  @Override
  public Set<Map.Entry<K, V>> entrySet() {
    return new AbstractSet<Map.Entry<K,V>>() {
      @Override
      public Iterator<Map.Entry<K, V>> iterator() {
        return new Iterator<Map.Entry<K,V>>() {

          Iterator<Map.Entry<K,SoftReference<V>>> iterator
          = map.entrySet().iterator();

          Map.Entry<K,V> next = prepare();

          public boolean hasNext() {
            return next != null;
          }

          public java.util.Map.Entry<K, V> next() {
            Map.Entry<K,V> rv = next;
            next = prepare();
            return rv;
          }

          public void remove() {
            // we would need to call iterator.remove() on the *previous* entry
            // in iterator, because iterator has already advanced in prepare()

            throw new UnsupportedOperationException("Cannot remove from" +
            " iterator on a SoftCache because of map consistency issues.");
          }

          public Map.Entry<K,V> prepare() {
            Map.Entry<K, SoftReference<V>> ref = null;
            while (ref == null || ref.getValue().get() == null) {
              if (!iterator.hasNext()) {
                return null;
              }
              ref = iterator.next();
            }

            return new ImmutableEntry<K,V>(ref.getKey(), ref.getValue().get());
          }

        };
      }

      @Override
      public int size() {
        return map.size();
      }
    };
  }

  /**
   * Simple immutable entry.  This class can be replaced with
   * AbstractMap.SimpleImmutableEntry once we move to java 6.
   * 
   * @author dramage
   */
  private static class ImmutableEntry<K,V> implements Map.Entry<K,V> {

    private K key;
    private V value;

    private ImmutableEntry(K key, V value) {
      this.key = key;
      this.value = value;
    }

    public K getKey() {
      return key;
    }

    public V getValue() {
      return value;
    }

    public V setValue(V value) {
      throw new UnsupportedOperationException("SoftCache EntrySet" +
      " is immutable");
    }

    @Override
    public int hashCode() {
      return this.key.hashCode() * 31 + this.value.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Map.Entry)) {
        return false;
      }

      Map.Entry<?,?> other = (Map.Entry<?,?>) o;
      return this.key.equals(other.getKey()) && this.value.equals(other.getValue());
    }
  }
}
