package edu.stanford.nlp.util;

import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;

/** A map over small non-negative integers, stored efficiently in an array.
 *  @author grenager
 */
public class PositiveIntMap {

  private final int[] map;


  /**
   * Puts the value in the map.
   * @return the value previously mapped to this key, or -1 if key not mapped
   * @throws IllegalArgumentException if the key is out of range
   */
  public int put(int key, int value) {
    if (key<0 || key>map.length) throw new IllegalArgumentException("Bad key for map with range " + map.length + ": " + key);
    if (value<0) throw new IllegalArgumentException("Bad value: " + value);
    int old = map[key];
    map[key] = value;
    return old;
  }

  /**
   * @return the value mapped to this key, or -1 if key not mapped
   */
  public int get(int key) {
    if (key<0 || key>map.length) throw new IllegalArgumentException();
    return map[key];
  }

  public int remove(int key) {
    if (key<0 || key>map.length) throw new IllegalArgumentException();
    int old = map[key];
    map[key] = -1;
    return old;
  }

  public Set<Integer> keySet() {
    Set<Integer> keySet = new HashSet<Integer>();
    for (int i=0; i<map.length; i++) {
      if (map[i]>=0) keySet.add(i);
    }
    return keySet;
  }

  public Set<Integer> values() {
    Set<Integer> values = new HashSet<Integer>();
    for (int i=0; i<map.length; i++) {
      if (map[i]>=0) values.add(map[i]);
    }
    return values;
  }

  public int getRange() {
    return map.length;
  }

  public boolean containsKey(int key) {
    if (key<0 || key>map.length) throw new IllegalArgumentException();
    return map[key]>=0;
  }

  public boolean containsValue(int value) {
    if (value<0) throw new IllegalArgumentException();
    for (int i=0; i<map.length; i++) {
      if (map[i]<0) continue;
      if (map[i]==value) return true;
    }
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PositiveIntMap)) return false;
    final PositiveIntMap f = (PositiveIntMap) o;
    if (!Arrays.equals(map, f.map)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(map);
  }

  @Override
  public String toString() {
    StringBuilder b = new StringBuilder();
    for (int i=0; i<map.length; i++) {
      if (map[i]<0) continue;
      if (b.length()==0) {
        b.append("{");
      } else {
        b.append(",");
      }
      int value = map[i];
      b.append((Object) i).append("=").append(value);
    }
    b.append("}");
    return b.toString();
  }

  public PositiveIntMap(int range) {
    map = new int[range];
    Arrays.fill(map, -1);
  }

  public PositiveIntMap(PositiveIntMap frame) {
    this.map = new int[frame.map.length];
    System.arraycopy(frame.map, 0, map, 0, map.length);
  }

  public int size() {
    int size = 0;
    for (int val : map) {
      if (val >= 0) size++;
    }
    return size;
  }
}
