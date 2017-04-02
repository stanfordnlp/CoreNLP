package edu.stanford.nlp.util;

import java.util.function.Supplier;

/**
 * An instantiation of a lazy object.
 *
 * @author Gabor Angeli
 */
public abstract class Lazy<E> {
  private E implOrNull = null;

  public synchronized E get() {
    if (implOrNull == null) {
      implOrNull = compute();
    }
    return implOrNull;
  }

  protected abstract E compute();

  public E getIfDefined() {
    return implOrNull;
  }

  public static <E> Lazy<E> from(final E definedElement) {
    return new Lazy<E>() {
      @Override
      protected E compute() {
        return definedElement;
      }
    };
  }

  public static <E> Lazy<E> of(Supplier<E> fn) {
    return new Lazy<E>() {
      @Override
      protected E compute() {
        return fn.get();
      }
    };
  }
}
