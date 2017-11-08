package edu.stanford.nlp.ling;

import edu.stanford.nlp.util.TypesafeMap;

public interface AbstractCoreLabel extends AbstractToken, Label, TypesafeMap {

  /**
   * Return a non-null String value for a key. This method is included
   * for backwards compatibility with the removed class AbstractMapLabel.
   * It is guaranteed to not return null; if the key is not present or
   * has a null value, it returns the empty string ("").  It is only valid to
   * call this method when key is paired with a value of type String.
   *
   * @param <KEY> A key type with a String value
   * @param key The key to return the value of.
   * @return "" if the key is not in the map or has the value {@code null}
   *     and the String value of the key otherwise
   */
  <KEY extends Key<String>> String getString(Class<KEY> key);

  <KEY extends Key<String>> String getString(Class<KEY> key, String def);

}
