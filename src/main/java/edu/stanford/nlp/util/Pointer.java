package edu.stanford.nlp.util;

import java.io.Serializable;
import java.util.Optional;

/**
 * A pointer to an object, to get around not being able to access non-final
 * variables within an anonymous function.
 *
 * @author Gabor Angeli
 */
public class Pointer<T> implements Serializable {
  /** The serial version uid to ensure stable serialization. */
  private static final long serialVersionUID = 1L;

  /**
   * The value the pointer is set to, if it is set.
   */
  private T impl;

  /**
   * Create a pointer pointing nowhere.
   */
  public Pointer() {
    this.impl = null;
  }

  /**
   * Create a pointer pointing at the given object.
   *
   * @param impl The object the pointer is pointing at.
   */
  public Pointer(T impl) {
    this.impl = impl;
  }


  /**
   * Dereference the pointer.
   * If the pointer is pointing somewhere, the {@linkplain Optional optional} will be set.
   * Otherwise, the optional will be {@linkplain Optional#empty() empty}.
   */
  public Optional<T> dereference() {
    return Optional.ofNullable(impl);
  }


  /**
   * Set the pointer.
   *
   * @param impl The value to set the pointer to. If this is null, the pointer is unset.
   */
  public void set(T impl) {
    this.impl = impl;
  }


  /**
   * Set the pointer to a possible value.
   *
   * @param impl The value to set the pointer to. If this is {@linkplain Optional#empty empty}, the pointer is unset.
   */
  public void set(Optional<T> impl) {
    this.impl = impl.orElse(null);
  }
}
