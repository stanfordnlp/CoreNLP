package edu.stanford.nlp.util;

import java.util.*;

/**
 * This implements a map to a set of lists.
 * @author Eric Yeh
 *
 * @param <U> the key type
 * @param <V> the type of the list elements
 */
public class MapList<U,V> {
  /** The map from each key to its list of values. */
  protected Map<U, List<V>> map = Generics.newHashMap();

  /** Creates an empty MapList. */
  public MapList() { }

  /**
   * Appends a value to the list for the given key, creating the list if needed.
   *
   * @param key the key
   * @param val the value to append
   */
  public void add(U key, V val) {
    ensureList(key).add(val);
  }

  /**
   * Using the iterator order of values in the value, adds the
   * individual elements into the list under the given key.
   *
   * @param key the key
   * @param vals the values to append
   */
  public void add(U key, Collection<V> vals) {
    ensureList(key).addAll(vals);
  }

  /**
   * Returns the length of the list for the given key.
   *
   * @param key the key
   * @return the length of the key's list, or 0 if the key is absent
   */
  public int size(U key) {
    if (map.containsKey(key))
      return map.get(key).size();
    return 0;
  }

  /**
   * Returns whether the given key has a list (possibly empty).
   *
   * @param key the key
   * @return true if the key is present
   */
  public boolean containsKey(U key) {
    return map.containsKey(key);
  }

  /**
   * Returns the keys, as a live view of the underlying map's key set.
   *
   * @return the keys
   */
  public Collection<U> keySet() { return map.keySet(); }

  /**
   * Returns the value at the given position in the list for the given key.
   *
   * @param key the key
   * @param index the position in the key's list
   * @return the value, or null if the key is absent or {@code index} is at least the list's length
   */
  public V get(U key, int index) {
    if (map.containsKey(key)){
      List<V> list = map.get(key);
      if (index < list.size())
        return map.get(key).get(index);
    }
    return null;
  }


  /**
   * Returns the list for the given key, first adding an empty list if the key is absent.
   *
   * @param key the key
   * @return the (modifiable) list for the key
   */
  protected List<V> ensureList(U key) {
    if (map.containsKey(key))
      return map.get(key);
    List<V> newList = new ArrayList<>();
    map.put(key, newList);
    return newList;
  }
  
}
