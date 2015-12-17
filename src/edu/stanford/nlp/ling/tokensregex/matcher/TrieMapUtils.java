package edu.stanford.nlp.ling.tokensregex.matcher;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.CollectionFactory;
import edu.stanford.nlp.util.CollectionValuedMap;
import edu.stanford.nlp.util.MapFactory;
import edu.stanford.nlp.util.MutableDouble;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Utility functions for using trie maps
 *
 * @author Angel Chang
 */
public class TrieMapUtils {
  public static <K> Counter<Iterable<K>> trieMapCounter() {
    return new ClassicCounter<>(TrieMapUtils.<K, MutableDouble>trieMapFactory());
  }

  public static <K,V> CollectionValuedMap<Iterable<K>, V> collectionValuedTrieMap() {
    return new CollectionValuedMap<>(
            TrieMapUtils.<K, Collection<V>>trieMapFactory(),
            CollectionFactory.<V>hashSetFactory(),
            false);
  }

  public static <K,V> CollectionValuedMap<Iterable<K>, V> collectionValuedTrieMap(CollectionFactory<V> collectionFactory) {
    return new CollectionValuedMap<>(
            TrieMapUtils.<K, Collection<V>>trieMapFactory(),
            collectionFactory,
            false);
  }

  @SuppressWarnings("unchecked")
  public static <K,V> MapFactory<Iterable<K>,V> trieMapFactory() {
    return TRIE_MAP_FACTORY;
  }

  @SuppressWarnings("unchecked")
  private static final MapFactory TRIE_MAP_FACTORY = new TrieMapFactory();

  private static class TrieMapFactory<K,V> extends MapFactory<Iterable<K>,V> {

    private static final long serialVersionUID = 1;

    @Override
    public Map<Iterable<K>,V> newMap() {
      return new TrieMap<>();
    }

    @Override
    public Map<Iterable<K>,V> newMap(int initCapacity) {
      return new TrieMap<>(initCapacity);
    }

    @Override
    public Set<Iterable<K>> newSet() {
      return Collections.newSetFromMap(new TrieMap<>());
    }

    @Override
    public Set<Iterable<K>> newSet(Collection<Iterable<K>> init) {
      Set<Iterable<K>> set = Collections.newSetFromMap(new TrieMap<>());
      init.addAll(init);
      return set;
    }

    @Override
    public <K1, V1> Map<K1, V1> setMap(Map<K1, V1> map) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <K1, V1> Map<K1, V1> setMap(Map<K1,V1> map, int initCapacity) {
      throw new UnsupportedOperationException();
    }

  } // end class TrieMapFactory



}
