package edu.stanford.nlp.util;

import java.lang.ref.SoftReference;
import java.util.function.Supplier;

/**
 * An instantiation of a lazy object.
 *
 * @param <E> The type of the value
 * @author Gabor Angeli
 */
public abstract class Lazy<E> {

  /** Creates a lazy whose value has not yet been computed. */
  public Lazy() { }

  /** If this lazy should cache, this is the cached value. */
  private SoftReference<E> implOrNullCache = null;
  /** If this lazy should not cache, this is the computed value */
  private E implOrNull = null;


  /** For testing only: simulate a GC event. */
  void simulateGC() {
    if (implOrNullCache != null) {
      implOrNullCache.clear();
    }
  }

  /**
   * Get the value of this {@link Lazy}, computing it if necessary.
   *
   * @return The value, computed with {@link #compute()} if it is not yet defined
   */
  public synchronized E get() {
    E orNull = getIfDefined();
    if (orNull == null) {
      orNull = compute();
      if (isCache()) {
        implOrNullCache = new SoftReference<>(orNull);
      } else {
        implOrNull = orNull;
      }
    }
    assert orNull != null;
    return orNull;
  }


  /**
   * Compute the value of this lazy.
   *
   * @return The computed value, which should not be null
   */
  protected abstract E compute();


  /**
   * Specify whether this lazy should garbage collect its value if needed,
   * or whether it should force it to be persistent.
   *
   * @return true if the value is held by a soft reference and may be garbage collected
   */
  public abstract boolean isCache();

  /**
   * Get the value of this {@link Lazy} if it's been initialized, or else
   * return null.
   *
   * @return The value, or null if it has not been computed or has been garbage collected
   */
  public E getIfDefined() {
    if (implOrNullCache != null) {
      assert implOrNull == null;
      return implOrNullCache.get();
    } else {
      return implOrNull;
    }
  }

  /**
   * Check if this lazy has been garbage collected, if it is a cached value.
   * Useful for, e.g., clearing keys in a map when the values are already gone.
   *
   * @return true if this is a caching lazy whose value is not currently held
   *   (including when it has never been computed); always false for a non-caching lazy
   */
  public boolean isGarbageCollected() {
    return this.isCache() && (this.implOrNullCache == null || this.implOrNullCache.get() == null);
  }


  /**
   * Create a degenerate {@link Lazy}, which simply returns the given pre-computed
   * value.
   *
   * @param <E> The type of the value
   * @param definedElement The value to return
   * @return A non-caching lazy already holding the given value
   */
  public static <E> Lazy<E> from(final E definedElement) {
    Lazy<E> rtn = new Lazy<E>() {
      @Override
      protected E compute() {
        return definedElement;
      }

      @Override
      public boolean isCache() {
        return false;
      }
    };
    rtn.implOrNull = definedElement;
    return rtn;
  }


  /**
   * Create a lazy value from the given provider.
   * The provider is only called once on initialization.
   *
   * @param <E> The type of the value
   * @param fn The provider of the value
   * @return A non-caching lazy which computes its value with {@code fn}
   */
  public static <E> Lazy<E> of(Supplier<E> fn) {
    return new Lazy<E>() {
      @Override
      protected E compute() {
        return fn.get();
      }

      @Override
      public boolean isCache() {
        return false;
      }
    };
  }


  /**
   * Create a lazy value from the given provider, allowing the value
   * stored in the lazy to be garbage collected if necessary.
   * The value is then re-created by when needed again.
   *
   * @param <E> The type of the value
   * @param fn The provider of the value
   * @return A caching lazy which computes its value with {@code fn}
   */
  public static <E> Lazy<E> cache(Supplier<E> fn) {
    return new Lazy<E>() {
      @Override
      protected E compute() {
        return fn.get();
      }

      @Override
      public boolean isCache() {
        return true;
      }
    };
  }
}
