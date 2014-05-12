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
  public interface Key<VALUE> { }

  /**
   * Returns true if the map contains the given key.
   * todo [cdm 2014]: This is synonymous with containsKey(), but used less, so we should just eliminate it.
   */
  public <VALUE> boolean has(Class<? extends Key<VALUE>> key);

  /**
   * Returns the value associated with the given key or null if
   * none is provided.
   */
  public <VALUE> VALUE get(Class<? extends Key<VALUE>> key);

  /**
   * Associates the given value with the given type for future calls
   * to get.  Returns the value removed or null if no value was present.
   */
  public <VALUE> VALUE set(Class<? extends Key<VALUE>> key, VALUE value);

  /**
   * Removes the given key from the map, returning the value removed.
   */
  public <VALUE> VALUE remove(Class<? extends Key<VALUE>> key);

  /**
   * Collection of keys currently held in this map.  Some implementations may
   * have the returned set be immutable.
   */
  public Set<Class<?>> keySet();
  //public Set<Class<? extends Key<?>>> keySet();

  /**
   * Returns true if contains the given key.
   */
  public <VALUE> boolean containsKey(Class<? extends Key<VALUE>> key);

  /**
   * Returns the number of keys in the map.
   */
  public int size();

}
