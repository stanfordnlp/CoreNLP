package edu.stanford.nlp.util;

import java.util.Optional;

/**
 * A pointer to an object, to get around not being able to access non-final
 * variables within an anonymous function.
 *
 * @author Gabor Angeli
 */
public class Pointer<T> {

  private Optional<T> impl;

  public Pointer() {
    this.impl = Optional.empty();
  }

  @SuppressWarnings("UnusedDeclaration")
  public Pointer(T impl) {
    this.impl = Optional.of(impl);
  }

  public Optional<T> dereference() { return impl; }

  public void set(T impl) { this.impl = Optional.of(impl); }

  public void set(Optional<T> impl) { this.impl = impl.isPresent() ? impl : this.impl; }
}
