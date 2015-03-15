package edu.stanford.nlp.util;

import java.io.Serializable;
import java.util.*;

/**
 * Map backed by an Array.
 *
 * @author Dan Klein
 * @author Roger Levy
 */
public final class ArrayMap<K,V> extends AbstractMap<K,V> implements Serializable {

  private static final long serialVersionUID = 1L;

  private Entry<K,V>[] entryArray;
  private int capacity;
  private int size;

  static final class Entry<K,V> implements Map.Entry<K,V>, Serializable {

    private static final long serialVersionUID = 1L;

    @SuppressWarnings({"NonSerializableFieldInSerializableClass"})
    private K key;
    @SuppressWarnings({"NonSerializableFieldInSerializableClass"})
    private V value;

    public K getKey() {
      return key;
    }

    public V getValue() {
      return value;
    }

    public V setValue(V o) {
      V old = value;
      value = o;
      return old;
    }

    @Override
    public int hashCode() {
      return (getKey() == null ? 0 : getKey().hashCode()) ^ (getValue() == null ? 0 : getValue().hashCode());
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (! (o instanceof Entry)) {
        return false;
      }
      Entry e = (Entry) o;
      return (getKey() == null ? e.getKey() == null : getKey().equals(e.getKey())) && (getValue() == null ? e.getValue() == null : getValue().equals(e.getValue()));
    }


    Entry(K key, V value) {
      this.key = key;
      this.value = value;
    }

    public String toString() {
      return key + "=" + value;
    }
  }


  @SuppressWarnings("unchecked")
  public ArrayMap() {
    size = 0;
    capacity = 2;
    entryArray = new Entry[2];
  }

  @SuppressWarnings("unchecked")
  public ArrayMap(int capacity) {
    size = 0;
    this.capacity = capacity;
    entryArray = new Entry[capacity];
  }

  @SuppressWarnings("unchecked")
  public ArrayMap(Map<? extends K, ? extends V> m) {
	  size = 0;
	  capacity = m.size();
	  entryArray = new Entry[m.size()];
	  this.putAll(m);
  }

  @SuppressWarnings("unchecked")
  public ArrayMap(K[] keys, V[] values) {
    if (keys.length!=values.length) throw new IllegalArgumentException("different number of keys and values.");
    size = keys.length;
    capacity = size;
    entryArray = new Entry[size];
    for (int i=0; i<keys.length; i++) {
      entryArray[i] = new Entry(keys[i], values[i]);
    }
  }

  public static <K, V> ArrayMap<K, V> newArrayMap() {
    return new ArrayMap<K, V>();
  }

  public static <K, V> ArrayMap<K, V> newArrayMap(int capacity) {
    return new ArrayMap<K, V>(capacity);
  }

  @Override
  public Set<Map.Entry<K,V>> entrySet() {
    //throw new java.lang.UnsupportedOperationException();
    return new HashSet<Map.Entry<K, V>>(Arrays.asList(entryArray).subList(0, size)) {
      private static final long serialVersionUID = 2746535724049192751L;
      @Override
      public boolean remove(Object o) {
        if (o instanceof Map.Entry) {
          Map.Entry entry = (Map.Entry) o;
          ArrayMap.this.remove(entry.getKey());
          return super.remove(o);
        } else {
          return false;
        }
      }
      @Override
      public void clear() {
        super.clear();
        ArrayMap.this.clear();
      }
    };
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public boolean isEmpty() {
    return size == 0;
  }

  @SuppressWarnings("unchecked")
  private void resize() {
    Entry<K,V>[] oldEntryArray = entryArray;
    int newCapacity = 2*size;
    if (newCapacity==0) newCapacity=1;
    entryArray = new Entry[newCapacity];
    System.arraycopy(oldEntryArray, 0, entryArray, 0, size);
    capacity = newCapacity;
  }

  @Override
  public void clear() {
    size = 0;
  }

  @Override
  public V put(K key, V val) {
    for (int i = 0; i < size; i++) {
      if (key.equals(entryArray[i].getKey())) {
        return entryArray[i].setValue(val);
      }
    }
    if (capacity <= size) {
      resize();
    }
    entryArray[size] = new Entry<K,V>(key, val);
    size++;
    return null;
  }

  @Override
  public V get(Object key) {
    for (int i = 0; i < size; i++) {
      if (key == null ? entryArray[i].getKey() == null : key.equals(entryArray[i].getKey())) {
        return entryArray[i].getValue();
      }
    }
    return null;
  }

  @Override
  public V remove(Object key) {
    for (int i = 0; i < size; i++) {
      if (key == null ? entryArray[i].getKey() == null : key.equals(entryArray[i].getKey())) {
        V value = entryArray[i].getValue();
        if (size > 1) {
          entryArray[i] = entryArray[size - 1];
        }
        size--;
        return value;
      }
    }
    return null;
  }

  protected int hashCodeCache; // = 0;

  @Override
  public int hashCode() {
    if (hashCodeCache == 0) {
      // this is now the djb2 (Dan Bernstein) hash; it used to be the awful K&R 1st ed. hash which simply summed hash codes, but that's very bad form.
      int hashCode = 5381;
      for (int i = 0; i < size; i++) {
        hashCode = hashCode * 33 + entryArray[i].hashCode();
      }
      hashCodeCache = hashCode;
    }
    return hashCodeCache;
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if ( ! (o instanceof Map)) {
      return false;
    }
    Map<K,V> m = (Map<K,V>) o;
    for (int i = 0; i < size; i++) {
      Object mVal = m.get(entryArray[i].getKey());
      if (mVal == null) {
        if (entryArray[i] != null) {
          return false;
        } else {
          continue;
        }
      }
      if (!m.get(entryArray[i].getKey()).equals(entryArray[i].getValue())) {
        return false;
      }
    }
    return true;
  }

}
