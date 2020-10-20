package edu.stanford.nlp.util;

import java.util.HashMap;
import java.util.Map;

/**
 * LRU cache for whatever.  Maps key K to value V.
 * <br>
 * If too many things are stored, values start falling out in the
 * order they were inserted.  Values that are accessed get their
 * position in the queue refreshed.
 * <br>
 * Works by keeping a linked list of nodes, implemented as a private
 * static inner class, and a map from K to the nodes.  Needing to keep
 * track of the nodes is the reason for not using a
 * java.util.LinkedList.
 * <br>
 * Could theoretically include most or all of the Map interface, but
 * so far only what was actually used has been implemented.
 *
 * @author John Bauer
 */

public class LeastRecentlyUsedCache<K, V> {
  static private class Node<K, V> {
    Node<K, V> next;
    Node<K, V> prev;

    K key;
    V value;

    Node(K key, V value) {
      this.key = key;
      this.value = value;
    }
  }

  static private class LinkedList<K, V> {
    final Node<K, V> BEGIN;
    final Node<K, V> END;
    int size = 0;

    LinkedList() {
      BEGIN = new Node<K, V>(null, null);
      END = new Node<K, V>(null, null);
      BEGIN.next = END;
      END.prev = BEGIN;
    }

    void remove(Node<K, V> node) {
      Node<K, V> next = node.next;
      Node<K, V> prev = node.prev;
      next.prev = prev;
      prev.next = next;
      size = size - 1;
    }

    Node<K, V> pop() {
      if (size == 0) {
        throw new IndexOutOfBoundsException();
      }
      Node<K, V> node = BEGIN.next;
      remove(BEGIN.next);
      return node;
    }

    Node<K, V> push(Node<K, V> node) {
      Node<K, V> prev = END.prev;
      node.next = END;
      node.prev = prev;
      prev.next = node;
      END.prev = node;
      size = size + 1;
      return node;
    }

    Node<K, V> push(K key, V value) {
      Node<K, V> node = new Node<K, V>(key, value);
      return push(node);
    }
  }

  private Map<K, Node<K, V>> map = new HashMap<>();
  private LinkedList<K, V> list = new LinkedList<>();
  private final int maxSize;

  public LeastRecentlyUsedCache(int maxSize) {
    this.maxSize = maxSize;
  }

  public V getOrDefault(K key, V defaultValue) {
    Node<K, V> node = map.getOrDefault(key, null);
    if (node == null) {
      return defaultValue;
    }
    // move the node to the back
    list.remove(node);
    list.push(node);
    return node.value;
  }

  public void add(K key, V value) {
    Node<K, V> node = map.getOrDefault(key, null);
    if (node != null) {
      list.remove(node);
    }
    node = list.push(key, value);
    map.put(key, node);
    if (list.size > maxSize) {
      node = list.pop();
      map.remove(node.key);
    }
  }

  public int size() {
    return list.size;
  }
}
