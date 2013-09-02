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
   */
  public <VALUE, KEY extends Key<VALUE>>
    boolean has(Class<KEY> key);

  /**
   * Returns the value associated with the given key or null if
   * none is provided.
   */
  public <VALUE, KEY extends Key<VALUE>>
    VALUE get(Class<KEY> key);

  /**
   * Associates the given value with the given type for future calls
   * to get.  Returns the value removed or null if no value was present.
   */
  public <VALUEBASE, VALUE extends VALUEBASE, KEY extends Key<VALUEBASE>>
    VALUE set(Class<KEY> key, VALUE value);

  /**
   * Removes the given key from the map, returning the value removed.
   */
  public <VALUE, KEY extends Key<VALUE>>
    VALUE remove(Class<KEY> key);

  /**
   * Collection of keys currently held in this map.  Some implementations may
   * have the returned set be immutable.
   */
  public Set<Class<?>> keySet();
  //public Set<Class<? extends Key<?>>> keySet();

  /**
   * Returns true if contains the given key.
   */
  public <VALUE, KEY extends Key<VALUE>>
    boolean containsKey(Class<KEY> key);

  /**
   * Returns the number of keys in the map.
   */
  public int size();

}
