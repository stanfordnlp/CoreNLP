package edu.stanford.nlp.util;

import java.util.Optional;
import java.util.function.Function;

import java.io.Serializable;
import java.util.Iterator;
import java.util.NoSuchElementException;

import static edu.stanford.nlp.util.ErasureUtils.uncheckedCast;
import static edu.stanford.nlp.util.logging.Redwood.Util.fatal;

/**
 * An implementation of the Maybe monad.
 * This object can either be an instance of Just, if it is filled with an object,
 * or an instance of Nothing if it is not. It is intended as an explicit alternative to
 * Java's null, (a) forcing the user to deal with null explicitly, and (b) providing
 * convenient methods for propagating either a value or null (see orElse or map).
 *
 * @author Gabor Angeli
 */
@SuppressWarnings("UnusedDeclaration")

public abstract class CoreMaybe<T> implements Iterable<T>, Serializable {
  private static final long serialVersionUID = 1L;

  private static class MaybeNotDefinedException extends RuntimeException {
    private static final long serialVersionUID = 5419343681628130137L;
    private MaybeNotDefinedException() { }
    private MaybeNotDefinedException(String message) {
      super(message);
    }
  }

  private CoreMaybe(){ } // Hide constructor

  /** True if there is a value stored (is an instance of Just) */
  public boolean isJust() { return false; }
  /** True if there is no value stored (is an instance of Nothing) */
  public boolean isNothing() { return false; }
  /** True if there is a value stored */
  public boolean isDefined() { return isJust(); }

  /** Get the value in this Maybe, or exception */
  public T get(){ return iterator().next(); }
  /** Get the value in this Maybe, or else return a default value */
  public T getOrElse(T defaultValue) {
    if (isDefined()) { return get(); } else { return defaultValue; }
  }
  /** Return a new Maybe with either the value provided, or an alternative */
  public CoreMaybe<T> orElse(CoreMaybe<T> alternative) {
    if (isDefined()){ return this; } else { return alternative; }
  }
  /** Get the value in this Maybe, or null if it is not defined */
  public T orNull() {
    if (isDefined()) { return get(); } else { return null; }
  }
  /** Get the value of this Maybe, or throw an exception */
  public T orException() {
    if (isDefined()) { return get(); } else { throw new MaybeNotDefinedException(); }
  }
  /** Get the value of this Maybe, or crash the program and all its threads */
  public T orCrash() {
    if (isDefined()) { return get(); } else { fatal(new MaybeNotDefinedException()); System.exit(1); return null; }
  }
  public T orCrash(Object msg) {
    if (isDefined()) { return get(); } else { fatal(new MaybeNotDefinedException(msg.toString())); System.exit(1); return null; }
  }
  public boolean equalsOrElse(Object other, boolean orElse) {
    if (isDefined()) { return get().equals(other); }
    else { return orElse; }
  }

  public String toStringOrElse(String alternative) {
    if (isDefined()) return get().toString();
    else return alternative;
  }

  /** Map the value of this Maybe. If a value is provided, the function is applied to it. Otherwise, Nothing is returned */
  public <U> CoreMaybe<U> map(Function<T,U> fn) {
    if (isDefined()) { return Just(fn.apply(get())); } else { return Nothing(); }
  }

  /** Return a Maybe if both this Maybe is defined, and the result of applying it to the function is defined */
  public <U> CoreMaybe<U> flatMap(Function<T,CoreMaybe<U>> fn) {
    if (isDefined()) { return fn.apply(get()); } else { return Nothing(); }
  }

  /** Apply a function to the value of this Maybe, if it exists */
  public <U> void foreach(Function<T,U> fn) { if (isDefined()) { fn.apply(get()); } }

  private static class Just<T> extends CoreMaybe<T> {
    private static final long serialVersionUID = 6058952254055493502L;
    public final T x;
    @SuppressWarnings("UnusedDeclaration")
    /** For reflection only */
    private Just() { this.x = null; }
    public Just(T x){ assert(x != null); this.x = x; }
    @Override
    public Iterator<T> iterator() {
      return new Iterator<T>() {
        private boolean hasElement = true;
        @Override
        public boolean hasNext() { return hasElement; }
        @Override
        public T next() {
          if (!hasElement) { throw new NoSuchElementException("Just holds only one value"); }
          hasElement = false;
          return x;
        }
        @Override
        public void remove() { }
      };
    }
    @Override
    public boolean isJust() { return true; }
    @Override
    public T get() { return x; }
    @Override
    public boolean equals(Object other) {
      if (other instanceof Just) {
        Just o = (Just) other;
        if (o.x instanceof Enum && this.x instanceof Enum) {
          // Java's enums are incompatible across runs
          return o.x.getClass().equals(this.x.getClass()) && ((Enum) o.x).name().equals(((Enum) this.x).name());
        } else {
          return o.x.equals(x);
        }
      } else {
        return false;
      }
    }
    @Override
    public int hashCode() {
      if (x instanceof Enum) {
        // Java's enums are incompatible across runs
        return ((Enum) x).name().hashCode();
      } else {
        return x.hashCode();
      }
    }
    @Override
    public String toString() { return "Just(" + x.toString() + ")"; }
  }

  private static class Nothing<T> extends CoreMaybe<T> {
    private static final long serialVersionUID = 7475159661351105480L;
    @Override
    public Iterator<T> iterator() {
      Optional<String> y = Optional.of("Foo");
      return new Iterator<T>() {
        @Override
        public boolean hasNext() { return false; }
        @Override
        public T next() { throw new NoSuchElementException("Cannot get element of Nothing"); }
        @Override
        public void remove() { }
      };
    }
    @Override
    public boolean isNothing() { return true; }
    @Override
    public boolean equals(Object other) { return (other instanceof Nothing); }
    @Override
    public int hashCode() { return 42; }
    @Override
    public String toString() { return "Nothing"; }
  }

  private static final CoreMaybe nothingSingleton = new Nothing();

  /** Create a new Nothing. To be used when you're tempted to use null */
  public static <T> CoreMaybe<T> Nothing() { return uncheckedCast(nothingSingleton); }

  /** Create a new Maybe with its value filled */
  public static <T> CoreMaybe<T> Just(T x) { return new Just<T>(x); }

  public static <E> CoreMaybe<E> fromString(String serialized) {
    String original = serialized;
    serialized = serialized.trim();
    if (serialized.equalsIgnoreCase("Nothing") || serialized.equalsIgnoreCase("None") || serialized.equalsIgnoreCase("null")) {
      return CoreMaybe.Nothing();
    } else {
      if (serialized.startsWith("Just(") && serialized.endsWith(")")){
        serialized = serialized.substring("Just(".length(), serialized.length() - 1).trim();
      }
      E value = MetaClass.castWithoutKnowingType(serialized);
      if (value != null) {
        return CoreMaybe.Just(value);
      } else {
        throw new IllegalArgumentException("Could not create Maybe from String: " + original + " -- could not cast: " + serialized);
      }
    }
  }

  public static <E> CoreMaybe<E> fromNull(E elemOrNull) {
    if (elemOrNull == null) return Nothing();
    else return Just(elemOrNull);
  }

  public static <E> CoreMaybe<E> fromOptional(Optional<E> optional) {
    return optional.isPresent() ? CoreMaybe.Just(optional.get()) : CoreMaybe.Nothing();
  }

}
