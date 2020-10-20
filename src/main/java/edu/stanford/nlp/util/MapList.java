package edu.stanford.nlp.util;

import java.util.*;

/**
 * This implements a map to a set of lists.
 * @author Eric Yeh
 *
 * @param <U>
 * @param <V>
 */
public class MapList<U,V> {
  protected Map<U, List<V>> map = Generics.newHashMap();

  public MapList() { }

  public void add(U key, V val) {
    ensureList(key).add(val);
  }

  /**
   * Using the iterator order of values in the value, adds the
   * individual elements into the list under the given key.
   */
  public void add(U key, Collection<V> vals) {
    ensureList(key).addAll(vals);
  }

  public int size(U key) {
    if (map.containsKey(key))
      return map.get(key).size();
    return 0;
  }

  public boolean containsKey(U key) {
    return map.containsKey(key);
  }

  public Collection<U> keySet() { return map.keySet(); }

  public V get(U key, int index) {
    if (map.containsKey(key)){
      List<V> list = map.get(key);
      if (index < list.size())
        return map.get(key).get(index);
    }
    return null;
  }


  protected List<V> ensureList(U key) {
    if (map.containsKey(key))
      return map.get(key);
    List<V> newList = new ArrayList<>();
    map.put(key, newList);
    return newList;
  }
  
}
