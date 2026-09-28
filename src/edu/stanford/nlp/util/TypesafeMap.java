package edu.stanford.nlp.util;

import java.util.Set;

/**
 * Type signature for a class that supports the basic operations required
 * of a typesafe heterogeneous map.
 *
 * @author dramage
 */
public interface TypesafeMap {

  /**
   * Base type of keys for the map.  The classes that implement Key are
   * the keys themselves - not instances of those classes.
   *
   * @param <VALUE> The type of the value associated with this key.
   */
  interface Key<VALUE> { }

  /**
   * Returns the value associated with the given key or null if
   * none is provided.
   *
   * @param <VALUE> the type of the value associated with the key
   * @param key the key class
   * @return the value associated with the key, or null
   */
  <VALUE> VALUE get(Class<? extends Key<VALUE>> key);

  /**
   * Associates the given value with the given type for future calls
   * to get.  Returns the value removed or null if no value was present.
   *
   * @param <VALUE> the type of the value associated with the key
   * @param key the key class
   * @param value the value to associate with the key
   * @return the previous value associated with the key, or null
   */
  <VALUE> VALUE set(Class<? extends Key<VALUE>> key, VALUE value);

  /**
   * Removes the given key from the map, returning the value removed.
   *
   * @param <VALUE> the type of the value associated with the key
   * @param key the key class
   * @return the value removed, or null if there was none
   */
  <VALUE> VALUE remove(Class<? extends Key<VALUE>> key);

  /**
   * Collection of keys currently held in this map.  Some implementations may
   * have the returned set be immutable.
   *
   * @return the key classes in this map
   */
  Set<Class<?>> keySet();
  // Set<Class<? extends Key<?>>> keySet();

  /**
   * Returns true if contains the given key.
   *
   * @param <VALUE> the type of the value associated with the key
   * @param key the key class
   * @return true if the map contains the key
   */
  <VALUE> boolean containsKey(Class<? extends Key<VALUE>> key);

  /**
   * Returns the number of keys in the map.
   *
   * @return the number of keys in the map
   */
  int size();

}
