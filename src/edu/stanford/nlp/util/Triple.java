package edu.stanford.nlp.util;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

import edu.stanford.nlp.util.logging.PrettyLoggable;
import edu.stanford.nlp.util.logging.PrettyLogger;
import edu.stanford.nlp.util.logging.Redwood.RedwoodChannels;

/**
 * Class representing an ordered triple of objects, possibly typed.
 * Useful when you'd like a method to return three objects, or would like to put
 * triples of objects in a Collection or Map. equals() and hashcode() should
 * work properly.
 *
 * @param <T1> The type of the first element
 * @param <T2> The type of the second element
 * @param <T3> The type of the third element
 * @author Teg Grenager (grenager@stanford.edu)
 */
public class Triple<T1,T2,T3> implements Comparable<Triple<T1,T2,T3>>, Serializable, PrettyLoggable {

  private static final long serialVersionUID = -4182871682751645440L;
  /** The first element. */
  public T1 first;
  /** The second element. */
  public T2 second;
  /** The third element. */
  public T3 third;

  /**
   * Creates a Triple of the given elements.
   *
   * @param first The first element
   * @param second The second element
   * @param third The third element
   */
  public Triple(T1 first, T2 second, T3 third) {
    this.first = first;
    this.second = second;
    this.third = third;
  }

  /**
   * Returns the first element.
   *
   * @return The first element
   */
  public T1 first() {
    return first;
  }

  /**
   * Returns the second element.
   *
   * @return The second element
   */
  public T2 second() {
    return second;
  }

  /**
   * Returns the third element.
   *
   * @return The third element
   */
  public T3 third() {
    return third;
  }

  /**
   * Sets the first element.
   *
   * @param o The new first element
   */
  public void setFirst(T1 o) {
    first = o;
  }

  /**
   * Sets the second element.
   *
   * @param o The new second element
   */
  public void setSecond(T2 o) {
    second = o;
  }

  /**
   * Sets the third element.
   *
   * @param o The new third element
   */
  public void setThird(T3 o) {
    third = o;
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean equals(Object o) {

    if (this == o) {
      return true;
    }

    if ( ! (o instanceof Triple)) {
      return false;
    }

    final Triple<T1,T2,T3> triple = (Triple<T1,T2,T3>) o;

    return Objects.equals(first, triple.first) && Objects.equals(second, triple.second) &&
            Objects.equals(third, triple.third);
  }

  @Override
  public int hashCode() {
    int result;
    result = (first != null ? first.hashCode() : 0);
    result = 29 * result + (second != null ? second.hashCode() : 0);
    result = 29 * result + (third != null ? third.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "(" + first + "," + second + "," + third + ")";
  }
  

  /**
   * Returns the three elements as a list.
   *
   * @return A new list containing the three elements in order
   */
  public List<Object> asList() {
    return CollectionUtils.makeList(first, second, third);
  }

  /**
   * Returns a Triple constructed from X, Y, and Z. Convenience method; the
   * compiler will disambiguate the classes used for you so that you don't have
   * to write out potentially long class names.
   *
   * @param <X> The type of the first element
   * @param <Y> The type of the second element
   * @param <Z> The type of the third element
   * @param x The first element
   * @param y The second element
   * @param z The third element
   * @return A new Triple of x, y, and z
   */
  public static <X, Y, Z> Triple<X, Y, Z> makeTriple(X x, Y y, Z z) {
    return new Triple<>(x, y, z);
  }

  /**
   * {@inheritDoc}
   */
  public void prettyLog(RedwoodChannels channels, String description) {
    PrettyLogger.log(channels, description, this.asList());
  }

  @SuppressWarnings("unchecked")
  @Override
  public int compareTo(Triple<T1, T2, T3> another) {
    int comp = ((Comparable<T1>) first()).compareTo(another.first());
    if (comp != 0) {
      return comp;
    } else {
      comp = ((Comparable<T2>) second()).compareTo(another.second());
      if (comp != 0) {
        return comp;
      } else {
        return ((Comparable<T3>) third()).compareTo(another.third());
      }
    }
  }

}
