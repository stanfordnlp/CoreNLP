package edu.stanford.nlp.util;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;


/**
 * Static utilities for manipulating indexes.
 *
 * @author dramage
 */
public class Indexes {

  private Indexes() {
  }


  /**
   * Returns a view of the given map backed by the given index, where
   * keys missing from the index cannot be added to it.
   */
  public static <K,V, M extends Map<Integer,V>> Map<K,V> view(final M map, final Index<K> index) {
    return view(map, index, false);
  }

  /**
   * Returns a view of the given map where each key is mapped from
   * Integer to type K via the given index.  Keys missing from the index
   * will be added to the index if mutableIndex is true; otherwise an
   * exception is thrown.
   */
  public static <K,V, M extends Map<Integer,V>> Map<K,V> view(final M map, final Index<K> index, final boolean mutableIndex) {
    return new AbstractMap<K,V>() {
      @Override
      public Set<Map.Entry<K, V>> entrySet() {
        return new AbstractSet<Map.Entry<K,V>>() {
          final Set<Map.Entry<Integer,V>> entrySet = map.entrySet();

          @Override
          public Iterator<Map.Entry<K, V>> iterator() {
            return new Iterator<Map.Entry<K,V>>() {
              final Iterator<Map.Entry<Integer,V>> it = entrySet.iterator();

              public boolean hasNext() {
                return it.hasNext();
              }

              public java.util.Map.Entry<K, V> next() {
                return new Map.Entry<K, V>() {
                  final Map.Entry<Integer, V> entry = it.next();

                  public K getKey() {
                    return index.get(entry.getKey());
                  }

                  public V getValue() {
                    return entry.getValue();
                  }

                  public V setValue(V value) {
                    return entry.setValue(value);
                  }
                };
              }

              public void remove() {
                it.remove();
              }
            };
          }

          @Override
          public int size() {
            return entrySet.size();
          }
        };
      }


      @SuppressWarnings("unchecked")
      @Override
      public boolean containsKey(Object key) {
        final int i = index.indexOf((K)key);
        return i >= 0 && map.containsKey(i);
      }

      @SuppressWarnings("unchecked")
      @Override
      public V get(Object key) {
        final int i = index.indexOf((K)key);
        return i >= 0 ? map.get(i) : null;
      }

      @Override
      public V put(K key, V value) {
        final int i = index.indexOf(key, mutableIndex);
        if (i < 0) {
          throw new IllegalArgumentException("Attempt to set value for key with no corresponding index entry");
        }
        return map.put(i, value);
      }

      @SuppressWarnings("unchecked")
      @Override
      public V remove(Object key) {
        final int i = index.indexOf((K)key);
        return i >= 0 ? map.remove(i) : null;
      }
    };
  }

  /**
   * Returns a view of the input where each element has been looked
   * up in the index.
   */
  public static <E> Iterable<E> lookupEach(final Index<E> index,
      final Iterable<Integer> input) {

    return Iterables.transform(input, new Function<Integer,E>() {
      public E apply(Integer in) {
        return index.get(in);
      }
    });
  }

  /**
   * Returns a view of the input where each element has been indexed
   * in the given index.
   */
  public static <E> Iterable<Integer> indexEach(final Index<E> index,
      final Iterable<E> input, final boolean add) {

    return Iterables.transform(input, new Function<E,Integer>() {
      @Override
      public Integer apply(E in) {
        return index.indexOf(in, add);
      }
    });
  }

  /**
   * Calls indexEach(index, input, false) - i.e. does not index new items.
   */
  public static <E> Iterable<Integer> indexEach(final Index<E> index,
      final Iterable<E> input) {
    return indexEach(index, input, false);
  }

}
