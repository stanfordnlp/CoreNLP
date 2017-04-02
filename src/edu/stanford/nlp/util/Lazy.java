package edu.stanford.nlp.util;

import java.lang.ref.SoftReference;
import java.util.function.Supplier;

/**
 * An instantiation of a lazy object.
 *
 * @author Gabor Angeli
 */
public abstract class Lazy<E> {
  private SoftReference<E> implOrNull = null;

  public synchronized E get() {
    E elem = implOrNull == null ? null : implOrNull.get();
    if (elem == null) {
      elem = compute();
      implOrNull = new SoftReference<E>(elem);
    }
    return elem;
  }

  protected abstract E compute();

  public E getIfDefined() {
    return implOrNull == null ? null : implOrNull.get();
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
