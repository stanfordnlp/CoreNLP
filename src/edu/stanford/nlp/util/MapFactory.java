package edu.stanford.nlp.util;

import java.io.Serializable;
import java.util.*;

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


  /** Return a MapFactory that returns a HashMap.
   *  <i>Implementation note: This method uses the same trick as the methods
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
   *  <i>Implementation note: This method uses the same trick as the methods
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
   *  <i>Implementation note: This method uses the same trick as the methods
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

  /** Return a MapFactory that returns an TreeMap.
   *  <i>Implementation note: This method uses the same trick as the methods
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

  /** Return a MapFactory that returns an LinkedHashMap.
   *  <i>Implementation note: This method uses the same trick as the methods
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
   *  <i>Implementation note: This method uses the same trick as the methods
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
      return new IdentityHashMap<K,V>();
    }

    @Override
    public Map<K,V> newMap(int initCapacity) {
      return new IdentityHashMap<K,V>(initCapacity);
    }

    @Override
    public <K1, V1> Map<K1, V1> setMap(Map<K1,V1> map) {
      map = new IdentityHashMap<K1,V1>();
      return map;
    }

    @Override
    public <K1, V1> Map<K1, V1> setMap(Map<K1,V1> map, int initCapacity) {
      map = new IdentityHashMap<K1,V1>(initCapacity);
      return map;
    }

  } // end class IdentityHashMapFactory


  private static class WeakHashMapFactory<K,V> extends MapFactory<K,V> {

    private static final long serialVersionUID = 4790014244304941000L;

    @Override
    public Map<K,V> newMap() {
      return new WeakHashMap<K,V>();
    }

    @Override
    public Map<K,V> newMap(int initCapacity) {
      return new WeakHashMap<K,V>(initCapacity);
    }


    @Override
    public <K1, V1> Map<K1, V1> setMap(Map<K1,V1> map) {
      map = new WeakHashMap<K1,V1>();
      return map;
    }

    @Override
    public <K1, V1> Map<K1, V1> setMap(Map<K1,V1> map, int initCapacity) {
      map = new WeakHashMap<K1,V1>(initCapacity);
      return map;
    }

  } // end class WeakHashMapFactory


  private static class TreeMapFactory<K,V> extends MapFactory<K,V> {

    private static final long serialVersionUID = -9138736068025818670L;

    @Override
    public Map<K,V> newMap() {
      return new TreeMap<K,V>();
    }

    @Override
    public Map<K,V> newMap(int initCapacity) {
      return newMap();
    }


    @Override
    public <K1, V1> Map<K1, V1> setMap(Map<K1,V1> map) {
      map = new TreeMap<K1,V1>();
      return map;
    }

    @Override
    public <K1, V1> Map<K1, V1> setMap(Map<K1,V1> map, int initCapacity) {
      map = new TreeMap<K1,V1>();
      return map;
    }

  } // end class TreeMapFactory


  private static class LinkedHashMapFactory<K,V> extends MapFactory<K,V> {

    private static final long serialVersionUID = -9138736068025818671L;

    @Override
    public Map<K,V> newMap() {
      return new LinkedHashMap<K,V>();
    }

    @Override
    public Map<K,V> newMap(int initCapacity) {
      return newMap();
    }


    @Override
    public <K1, V1> Map<K1, V1> setMap(Map<K1,V1> map) {
      map = new LinkedHashMap<K1,V1>();
      return map;
    }

    @Override
    public <K1, V1> Map<K1, V1> setMap(Map<K1,V1> map, int initCapacity) {
      map = new LinkedHashMap<K1,V1>();
      return map;
    }

  } // end class LinkedHashMapFactory


  private static class ArrayMapFactory<K,V> extends MapFactory<K,V> {

    private static final long serialVersionUID = -5855812734715185523L;

    @Override
    public Map<K,V> newMap() {
      return new ArrayMap<K,V>();
    }

    @Override
    public Map<K,V> newMap(int initCapacity) {
      return new ArrayMap<K,V>(initCapacity);
    }

    @Override
    public <K1, V1> Map<K1, V1> setMap(Map<K1, V1> map) {
      return new ArrayMap<K1,V1>();
    }

    @Override
    public <K1, V1> Map<K1, V1> setMap(Map<K1,V1> map, int initCapacity) {
      map = new ArrayMap<K1,V1>(initCapacity);
      return map;
    }

  } // end class ArrayMapFactory


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
   * A method to get a parameterized (genericized) map out.
   *
   * @param map A type-parameterized {@link Map} argument
   * @return A {@link Map} with type-parameterization identical to that of
   *         the argument.
   */
  public abstract <K1, V1> Map<K1, V1> setMap(Map<K1,V1> map);

  public abstract <K1, V1> Map<K1, V1> setMap(Map<K1,V1> map, int initCapacity);

}
