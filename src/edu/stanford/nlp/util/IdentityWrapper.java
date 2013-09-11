package edu.stanford.nlp.util;

/**
 * Wrapper to make an object use identity equality and hashcode.  Useful for tricking a WeakHashMap
 * (which exists) into being a WeakIdentityHashMap (which doesn't) or making a HashSet into an IdentityHashSet.
 *  Two identity wrappers are equal iff their getObjects are the same object.
 *
 * @author Dan Klein (klein@cs.stanford.edu)
 */
public class IdentityWrapper<T> {
  T object;

  public T getObject() {
    return object;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(object);
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean equals(Object o) {
    if (o instanceof IdentityWrapper) {
      return getObject() == ((IdentityWrapper) o).getObject();
    }
    return false;
  }

  @Override
  public String toString() {
    return object.toString();
  }

  public IdentityWrapper(T o) {
    object = o;
  }
}
