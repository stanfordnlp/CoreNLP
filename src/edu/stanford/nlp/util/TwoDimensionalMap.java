package edu.stanford.nlp.util;

import java.util.*;
import java.io.Serializable;
import java.util.function.Function;

import edu.stanford.nlp.util.MapFactory;

/**
 * @author grenager
 */
public class TwoDimensionalMap<K1, K2, V> implements Serializable, Iterable<TwoDimensionalMap.Entry<K1, K2, V>> {

  private static final long serialVersionUID = 2L;
  private final MapFactory<K1, Map<K2, V>> mf1;
  private final MapFactory<K2, V> mf2;
  Map<K1, Map<K2, V>> map;
  
  public int size() {
    int size = 0;
    for (Map.Entry<K1, Map<K2, V>> entry : map.entrySet()) {
      size += (entry.getValue().size());
    }
    return size;
  }

  public boolean isEmpty() {
    for (Map.Entry<K1, Map<K2, V>> entry : map.entrySet()) {
      if (!entry.getValue().isEmpty()) {
        return false;
      }
    }
    return true;
  }

  public V put(K1 key1, K2 key2, V value) {
    Map<K2, V> m = getMap(key1);
    return m.put(key2, value);
  }

  // adds empty hashmap for key key1
  public void put(K1 key1) {
    map.put(key1, mf2.newMap());
  }

  public boolean contains(K1 key1, K2 key2) {
    if (!containsKey(key1)) {
      return false;
    }
    return getMap(key1).containsKey(key2);
  }

  public V get(K1 key1, K2 key2) {
    Map<K2, V> m = getMap(key1);
    return m.get(key2);
  }

  public V remove(K1 key1, K2 key2) {
    return get(key1).remove(key2);
  }

  /**
   * Removes all of the data associated with the first key in the map
   */
  public void remove(K1 key1) {
    map.remove(key1);
  }

  public void clear() {
    map.clear();
  }

  public boolean containsKey(K1 key1) {
    return map.containsKey(key1);
  }

  public Map<K2, V> get(K1 key1) {
    return getMap(key1);
  }

  public Map<K2, V> getMap(K1 key1) {
    Map<K2, V> m = map.get(key1);
    if (m == null) {
      m = mf2.newMap();
      map.put(key1, m);
    }
    return m;
  }

  public Collection<V> values() {
    // TODO: Should return a specialized class
    List<V> s = Generics.newArrayList();
    for (Map<K2, V> innerMap : map.values()) {
      s.addAll(innerMap.values());
    }
    return s;
  }

  public Set<K1> firstKeySet() {
    return map.keySet();
  }

  public Set<K2> secondKeySet() {
    Set<K2> keys = Generics.newHashSet();
    for (K1 k1 : map.keySet()) {
      keys.addAll(get(k1).keySet());
    }
    return keys;
  }

  /**
   * Adds all of the entries in the <code>other</code> map, performing
   * <code>function</code> on them to transform the values
   */
  public <V2> void addAll(TwoDimensionalMap<? extends K1, ? extends K2, ? extends V2> other, Function<V2, V> function) {
    for (TwoDimensionalMap.Entry<? extends K1, ? extends K2, ? extends V2> entry : other) {
      put(entry.getFirstKey(), entry.getSecondKey(), function.apply(entry.getValue()));
    }
  }

  /**
   * Transforms this map into a new map using the given transform function. <br>
   * Assumes that the map factory which produced &lt;K1, K2, V&gt; maps will
   * happily produce &lt;K1, K2, V2&gt; maps.  If that is not true, then
   * this will fail horribly, hopefully right away.
   */
  public <V2> TwoDimensionalMap<K1, K2, V2> transform(Function<V, V2> function) {
    MapFactory<K1, Map<K2, V2>> newMF1 = ErasureUtils.uncheckedCast(mf1);
    MapFactory<K2, V2> newMF2 = ErasureUtils.uncheckedCast(mf2);
    TwoDimensionalMap<K1, K2, V2> newMap = new TwoDimensionalMap<K1, K2, V2>(newMF1, newMF2);
    newMap.addAll(this, function);
    return newMap;
  }

  /**
   * Replace each of the elements with the application of a function.
   *
   * TODO: use a TriFunction?  Such a thing does not exist
   */
  public void replaceAll(Function<V, ? extends V> f) {
    for (K1 k : map.keySet()) {
      map.get(k).replaceAll((x, y) -> f.apply(y));
    }
  }

  public TwoDimensionalMap() {
    this(MapFactory.<K1, Map<K2, V>>hashMapFactory(), MapFactory.<K2, V>hashMapFactory());
  }

  public TwoDimensionalMap(TwoDimensionalMap<K1, K2, V> tdm) {
    this(tdm.mf1, tdm.mf2);
    for (K1 k1 : tdm.map.keySet()) {
      Map<K2, V> m = tdm.map.get(k1);
      Map<K2, V> copy = mf2.newMap();
      copy.putAll(m);
      this.map.put(k1, copy);
    }
  }

  public TwoDimensionalMap(MapFactory<K1, Map<K2, V>> mf1, MapFactory<K2, V> mf2) {
    this.mf1 = mf1;
    this.mf2 = mf2;
    this.map = mf1.newMap();
  }

  public static <K1, K2, V> TwoDimensionalMap<K1, K2, V> hashMap() {
    return new TwoDimensionalMap<>(MapFactory.<K1, Map<K2, V>>hashMapFactory(), MapFactory.<K2, V>hashMapFactory());
  }

  public static <K1, K2, V> TwoDimensionalMap<K1, K2, V> treeMap() {
    return new TwoDimensionalMap<>(MapFactory.<K1, Map<K2, V>>treeMapFactory(), MapFactory.<K2, V>treeMapFactory());
  }

  public static <K1, K2, V> TwoDimensionalMap<K1, K2, V> identityHashMap() {
    return new TwoDimensionalMap<>(MapFactory.<K1, Map<K2, V>>identityHashMapFactory(), MapFactory.<K2, V>identityHashMapFactory());
  }

  @Override
  public String toString() {
    return map.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof TwoDimensionalMap)) {
      return false;
    }
    TwoDimensionalMap<?, ?, ?> other = (TwoDimensionalMap<?, ?, ?>) o;
    return map.equals(other.map);
  }

  @Override
  public int hashCode() {
    return map.hashCode();
  }

  /**
   * Iterate over the map using the iterator and entry inner classes.
   */
  public Iterator<Entry<K1, K2, V>> iterator() {
    return new TwoDimensionalMapIterator<>(this);
  }

  public Iterator<V> valueIterator() {
    return new TwoDimensionalMapValueIterator<>(this);
  }

  static class TwoDimensionalMapValueIterator<K1, K2, V> implements Iterator<V> {
    Iterator<Entry<K1, K2, V>> entryIterator;

    TwoDimensionalMapValueIterator(TwoDimensionalMap<K1, K2, V> map) {
      entryIterator = map.iterator();
    }

    public boolean hasNext() {
      return entryIterator.hasNext();
    }

    public V next() {
      Entry<K1, K2, V> next = entryIterator.next();
      return next.getValue();
    }

    public void remove() {
      entryIterator.remove();
    }
  }

  /**
   * This inner class represents a single entry in the TwoDimensionalMap.  
   * Iterating over the map will give you these.
   */
  public static class Entry<K1, K2, V> {
    K1 firstKey;
    K2 secondKey;
    V value;

    Entry(K1 k1, K2 k2, V v) { 
      firstKey = k1;
      secondKey = k2;
      value = v;
    }

    public K1 getFirstKey() { return firstKey; }
    public K2 getSecondKey() { return secondKey; }
    public V getValue() { return value; }

    @Override
    public String toString() {
      return "(" + firstKey + "," + secondKey + "," + value + ")";
    }
  }

  /**
   * Internal class which represents an iterator over the data in the
   * TwoDimensionalMap.  It keeps state in the form of an iterator
   * over the outer map, which maps keys to inner maps, and an
   * iterator over the most recent inner map seen.  When the inner map
   * has been completely iterated over, the outer map iterator
   * advances one step.  The iterator is finished when all key pairs
   * have been returned once.
   */
  static class TwoDimensionalMapIterator<K1, K2, V> implements Iterator<Entry<K1, K2, V>> {
    Iterator<Map.Entry<K1, Map<K2, V>>> outerIterator;
    Iterator<Map.Entry<K2, V>> innerIterator;
    Entry<K1, K2, V> next;

    TwoDimensionalMapIterator(TwoDimensionalMap<K1, K2, V> map) {
      outerIterator = map.map.entrySet().iterator();
      primeNext();
    }

    public boolean hasNext() {
      return next != null;
    }

    public Entry<K1, K2, V> next() {
      if (next == null) {
        throw new NoSuchElementException();
      }
      Entry<K1, K2, V> result = next;
      primeNext();
      return result;
    }

    private void primeNext() {
      K1 k1 = null;
      if (next != null) {
        k1 = next.getFirstKey();
      }
      while (innerIterator == null || !innerIterator.hasNext()) {
        if (!outerIterator.hasNext()) {
          next = null;
          return;
        }
        Map.Entry<K1, Map<K2, V>> outerEntry = outerIterator.next();
        k1 = outerEntry.getKey();
        innerIterator = outerEntry.getValue().entrySet().iterator();
      }
      Map.Entry<K2, V> innerEntry = innerIterator.next();
      next = new Entry<>(k1, innerEntry.getKey(), innerEntry.getValue());
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}
