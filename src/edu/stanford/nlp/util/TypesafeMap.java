package edu.stanford.nlp.util;

import java.util.Set;

/**
 * Type signature for a class that supports the basic operations required
 * of a typesafe heterogeneous map.
 *
 * @param <BASE> A type-checking parameter that ensures that all keys
 * given to get, has, set, etc., are appropriate for this particular
 * TypesafeMap.
 *
 * @author dramage
 */
public interface TypesafeMap<BASE> {

  /**
   * Base type of keys for the map.  The classes that implement Key are
   * the keys themselves - not instances of those classes.
   *
   * @param <BASE> A type-checking parameter that ensures that keys sent
   * to get, has, set, etc., are appropriate for the TypesafeMap they are
   * sent to.
   *
   * @param <VALUE> The type of the value associated with this key.
   */
  public interface Key<BASE,VALUE> { }

  /**
   * Returns true if the map contains the given key.
   */
  public <VALUE, KEY extends Key<BASE,VALUE>>
    boolean has(Class<KEY> key);

  /**
   * Returns the value associated with the given key or null if
   * none is provided.
   */
  public <VALUE, KEY extends Key<BASE,VALUE>>
    VALUE get(Class<KEY> key);

  /**
   * Associates the given value with the given type for future calls
   * to get.  Returns the value removed or null if no value was present.
   */
  public <VALUEBASE, VALUE extends VALUEBASE, KEY extends Key<BASE,VALUEBASE>>
    VALUE set(Class<KEY> key, VALUE value);

  /**
   * Removes the given key from the map, returning the value removed.
   */
  public <VALUE, KEY extends Key<BASE,VALUE>>
    VALUE remove(Class<KEY> key);

  /**
   * Collection of keys currently held in this map.  Some implementations may
   * have the returned set be immutable.
   *
   * As of Java 1.6 Java's generics type inference is broken enough that
   * the proper signature for this method -
   * Set&lt;Class&lt;? extends Key&lt;BASE,?&gt;&gt;&gt; - cannot be
   * implemented by subclasses, so the return signature is not typed
   * as strongly as it could be.
   */
  public Set<Class<?>> keySet();

  /**
   * Returns true if contains the given key.
   */
  // todo: shouldn't CoreMap below really be BASE???
  public <VALUE, KEY extends Key<CoreMap, VALUE>>
    boolean containsKey(Class<KEY> key);

  /**
   * Returns the number of keys in the map.
   */
  public int size();

}
