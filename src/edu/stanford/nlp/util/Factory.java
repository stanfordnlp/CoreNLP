package edu.stanford.nlp.util;

import java.io.Serializable;

/**
 * A generified factory class which creates instances of a particular type.
 *
 * @author dramage
 */
public interface Factory<T> extends Serializable {

  /**
   * Creates and returns a new instance of the given type.
   *
   * @return A new instance of the type T
   */
  public T create();

}
