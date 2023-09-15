package edu.stanford.nlp.util;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A factory class for vending different sorts of Maps.
 *
 * @author Dan Klein (klein@cs.stanford.edu)
 * @author Kayur Patel (kdpatel@cs)
 */
public abstract class MapFactory<K,V> implements Serializable {

  // allow people to write subclasses
  protected MapFactory() {
  }

  private static final long serialVersionUID = 4529666940763477360L;

  @SuppressWarnings("unchecked")
  public static final MapFactory HASH_MAP_FACTORY = new HashMapFactory();

  @SuppressWarnings("unchecked")
  public static final MapFactory IDENTITY_HASH_MAP_FACTORY = new IdentityHashMapFactory();

  @SuppressWarnings("unchecked")
  private static final MapFactory WEAK_HASH_MAP_FACTORY = new WeakHashMapFactory();

  @SuppressWarnings("unchecked")
  private static final MapFactory TREE_MAP_FACTORY = new TreeMapFactory();

  @SuppressWarnings("unchecked")
  private static final MapFactory LINKED_HASH_MAP_FACTORY = new LinkedHashMapFactory();

  @SuppressWarnings("unchecked")
  private static final MapFactory ARRAY_MAP_FACTORY = new ArrayMapFactory();

  public static final MapFactory CONCURRENT_MAP_FACTORY = new ConcurrentMapFactory();

  /** Return a MapFactory that returns a HashMap.
   *  <i>Implementation note</i>: This method uses the same trick as the methods
   *  like emptyMap() introduced in the Collections class in JDK1.5 where
   *  callers can call this method with apparent type safety because this
   *  method takes the hit for the cast.
   *
   *  @return A MapFactory that makes a HashMap.
   */
  @SuppressWarnings("unchecked")
  public static <K,V> MapFactory<K,V> hashMapFactory() {
    return HASH_MAP_FACTORY;
  }

  /** Return a MapFactory that returns an IdentityHashMap.
   *  <i>Implementation note</i>: This method uses the same trick as the methods
   *  like emptyMap() introduced in the Collections class in JDK1.5 where
   *  callers can call this method with apparent type safety because this
   *  method takes the hit for the cast.
   *
   *  @return A MapFactory that makes a HashMap.
   */
  @SuppressWarnings("unchecked")
  public static <K,V> MapFactory<K,V> identityHashMapFactory() {
    return IDENTITY_HASH_MAP_FACTORY;
  }

  /** Return a MapFactory that returns a WeakHashMap.
   *  <i>Implementation note</i>: This method uses the same trick as the methods
   *  like emptyMap() introduced in the Collections class in JDK1.5 where
   *  callers can call this method with apparent type safety because this
   *  method takes the hit for the cast.
   *
   *  @return A MapFactory that makes a WeakHashMap.
   */
  @SuppressWarnings("unchecked")
  public static <K,V> MapFactory<K,V> weakHashMapFactory() {
    return WEAK_HASH_MAP_FACTORY;
  }

  /** Return a MapFactory that returns a TreeMap.
   *  <i>Implementation note</i>: This method uses the same trick as the methods
   *  like emptyMap() introduced in the Collections class in JDK1.5 where
   *  callers can call this method with apparent type safety because this
   *  method takes the hit for the cast.
   *
   *  @return A MapFactory that makes an TreeMap.
   */
  @SuppressWarnings("unchecked")
  public static <K,V> MapFactory<K,V> treeMapFactory() {
    return TREE_MAP_FACTORY;
  }

  /**
   * Return a MapFactory that returns a TreeMap with the given Comparator.
   */
  public static <K,V> MapFactory<K,V> treeMapFactory(Comparator<? super K> comparator) {
    return new TreeMapFactory<>(comparator);
  }

  /** Return a MapFactory that returns an LinkedHashMap.
   *  <i>Implementation note</i>: This method uses the same trick as the methods
   *  like emptyMap() introduced in the Collections class in JDK1.5 where
   *  callers can call this method with apparent type safety because this
   *  method takes the hit for the cast.
   *
   *  @return A MapFactory that makes an LinkedHashMap.
   */
  @SuppressWarnings("unchecked")
  public static <K,V> MapFactory<K,V> linkedHashMapFactory() {
    return LINKED_HASH_MAP_FACTORY;
  }

  /** Return a MapFactory that returns an ArrayMap.
   *  <i>Implementation note</i>: This method uses the same trick as the methods
   *  like emptyMap() introduced in the Collections class in JDK1.5 where
   *  callers can call this method with apparent type safety because this
   *  method takes the hit for the cast.
   *
   *  @return A MapFactory that makes an ArrayMap.
   */
  @SuppressWarnings("unchecked")
  public static <K,V> MapFactory<K,V> arrayMapFactory() {
    return ARRAY_MAP_FACTORY;
  }



  private static class HashMapFactory<K,V> extends MapFactory<K,V> {

    private static final long serialVersionUID = -9222344631596580863L;

    @Override
    public Map<K,V> newMap() {
      return Generics.newHashMap();
    }

    @Override
    public Map<K,V> newMap(int initCapacity) {
      return Generics.newHashMap(initCapacity);
    }

    @Override
    public Set<K> newSet() {
      return Generics.newHashSet();
    }

    @Override
    public Set<K> newSet(Collection<K> init) {
      return Generics.newHashSet(init);
    }

    @Override
    public <K1, V1> Map<K1, V1> setMap(Map<K1,V1> map) {
      map = Generics.newHashMap();
      return map;
    }

    @Override
    public <K1, V1> Map<K1, V1> setMap(Map<K1,V1> map, int initCapacity) {
      map = Generics.newHashMap(initCapacity);
      return map;
    }

  } // end class HashMapFactory


  private static class IdentityHashMapFactory<K,V> extends MapFactory<K,V> {

    private static final long serialVersionUID = -9222344631596580863L;

    @Override
    public Map<K,V> newMap() {
      return new IdentityHashMap<>();
    }

    @Override
    public Map<K,V> newMap(int initCapacity) {
      return new IdentityHashMap<>(initCapacity);
    }

    @Override
    public Set<K> newSet() {
      return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    @Override
    public Set<K> newSet(Collection<K> init) {
      Set<K> set =  Collections.newSetFromMap(new IdentityHashMap<>());  // nothing more efficient to be done here...
      set.addAll(init);
      return set;
    }

    @Override
    public <K1, V1> Map<K1, V1> setMap(Map<K1,V1> map) {
      map = new IdentityHashMap<>();
      return map;
    }

    @Override
    public <K1, V1> Map<K1, V1> setMap(Map<K1,V1> map, int initCapacity) {
      map = new IdentityHashMap<>(initCapacity);
      return map;
    }

  } // end class IdentityHashMapFactory


  private static class WeakHashMapFactory<K,V> extends MapFactory<K,V> {

    private static final long serialVersionUID = 4790014244304941000L;

    @Override
    public Map<K,V> newMap() {
      return new WeakHashMap<>();
    }

    @Override
    public Map<K,V> newMap(int initCapacity) {
      return new WeakHashMap<>(initCapacity);
    }

    @Override
    public Set<K> newSet() {
      return Collections.newSetFromMap(new WeakHashMap<>());
    }

    @Override
    public Set<K> newSet(Collection<K> init) {
      Set<K> set = Collections.newSetFromMap(new WeakHashMap<>());
      set.addAll(init);
      return set;
    }


    @Override
    public <K1, V1> Map<K1, V1> setMap(Map<K1,V1> map) {
      map = new WeakHashMap<>();
      return map;
    }

    @Override
    public <K1, V1> Map<K1, V1> setMap(Map<K1,V1> map, int initCapacity) {
      map = new WeakHashMap<>(initCapacity);
      return map;
    }

  } // end class WeakHashMapFactory


  private static class TreeMapFactory<K,V> extends MapFactory<K,V> {

    private static final long serialVersionUID = -9138736068025818670L;

    private final Comparator<? super K> comparator;

    public TreeMapFactory() {
      this.comparator = null;
    }

    public TreeMapFactory(Comparator<? super K> comparator) {
      this.comparator = comparator;
    }

    @Override
    public Map<K,V> newMap() {
      return comparator == null ? new TreeMap<>() : new TreeMap<>(comparator);
    }

    @Override
    public Map<K,V> newMap(int initCapacity) {
      return newMap();
    }

    @Override
    public Set<K> newSet() {
      return comparator == null ? new TreeSet<>() : new TreeSet<>(comparator);
    }

    @Override
    public Set<K> newSet(Collection<K> init) {
      return new TreeSet<>(init);
    }


    @Override
    public <K1, V1> Map<K1, V1> setMap(Map<K1,V1> map) {
      if (comparator == null) {
        throw new UnsupportedOperationException();
      }
      map = new TreeMap<>();
      return map;
    }

    @Override
    public <K1, V1> Map<K1, V1> setMap(Map<K1,V1> map, int initCapacity) {
      if (comparator == null) {
        throw new UnsupportedOperationException();
      }
      map = new TreeMap<>();
      return map;
    }

  } // end class TreeMapFactory

  private static class LinkedHashMapFactory<K,V> extends MapFactory<K,V> {

    private static final long serialVersionUID = -9138736068025818671L;

    @Override
    public Map<K,V> newMap() {
      return new LinkedHashMap<>();
    }

    @Override
    public Map<K,V> newMap(int initCapacity) {
      return newMap();
    }

    @Override
    public Set<K> newSet() {
      return new LinkedHashSet<>();
    }

    @Override
    public Set<K> newSet(Collection<K> init) {
      return new LinkedHashSet<>(init);
    }


    @Override
    public <K1, V1> Map<K1, V1> setMap(Map<K1,V1> map) {
      map = new LinkedHashMap<>();
      return map;
    }

    @Override
    public <K1, V1> Map<K1, V1> setMap(Map<K1,V1> map, int initCapacity) {
      map = new LinkedHashMap<>();
      return map;
    }

  } // end class LinkedHashMapFactory


  private static class ArrayMapFactory<K,V> extends MapFactory<K,V> {

    private static final long serialVersionUID = -5855812734715185523L;

    @Override
    public Map<K,V> newMap() {
      return new ArrayMap<>();
    }

    @Override
    public Map<K,V> newMap(int initCapacity) {
      return new ArrayMap<>(initCapacity);
    }

    @Override
    public Set<K> newSet() {
      return new ArraySet<>();
    }

    @Override
    public Set<K> newSet(Collection<K> init) {
      return new ArraySet<>();
    }

    @Override
    public <K1, V1> Map<K1, V1> setMap(Map<K1, V1> map) {
      return new ArrayMap<>();
    }

    @Override
    public <K1, V1> Map<K1, V1> setMap(Map<K1,V1> map, int initCapacity) {
      map = new ArrayMap<>(initCapacity);
      return map;
    }

  } // end class ArrayMapFactory


  private static class ConcurrentMapFactory<K,V> extends MapFactory<K,V> {

    private static final long serialVersionUID = -5855812734715185523L;

    @Override
    public Map<K,V> newMap() {
      return new ConcurrentHashMap<>();
    }

    @Override
    public Map<K,V> newMap(int initCapacity) {
      return new ConcurrentHashMap<>(initCapacity);
    }

    @Override
    public Set<K> newSet() {
      return Collections.newSetFromMap(new ConcurrentHashMap<>());
    }

    @Override
    public Set<K> newSet(Collection<K> init) {
      Set<K> set = Collections.newSetFromMap(new ConcurrentHashMap<>());
      set.addAll(init);
      return set;
    }

    @Override
    public <K1, V1> Map<K1, V1> setMap(Map<K1, V1> map) {
      return new ConcurrentHashMap<>();
    }

    @Override
    public <K1, V1> Map<K1, V1> setMap(Map<K1,V1> map, int initCapacity) {
      map = new ConcurrentHashMap<>(initCapacity);
      return map;
    }

  } // end class ConcurrentMapFactory

  /**
   * Returns a new non-parameterized map of a particular sort.
   *
   * @return A new non-parameterized map of a particular sort
   */
  public abstract Map<K,V> newMap();

  /**
   * Returns a new non-parameterized map of a particular sort with an initial capacity.
   *
   * @param initCapacity initial capacity of the map
   * @return A new non-parameterized map of a particular sort with an initial capacity
   */
  public abstract Map<K,V> newMap(int initCapacity);

  /**
   * A set with the same {@code K} parameterization of the Maps.
   */
  public abstract Set<K> newSet();

  /**
   * A set with the same {@code K} parameterization, but initialized to the given collection.
   */
  public abstract Set<K> newSet(Collection<K> init);

  /**
   * A method to get a parameterized (genericized) map out.
   *
   * @param map A type-parameterized {@link Map} argument
   * @return A {@link Map} with type-parameterization identical to that of
   *         the argument.
   */
  public abstract <K1, V1> Map<K1, V1> setMap(Map<K1,V1> map);

  public abstract <K1, V1> Map<K1, V1> setMap(Map<K1,V1> map, int initCapacity);

}
