package edu.stanford.nlp.util;

import java.io.Serializable;
import java.util.List;

import edu.stanford.nlp.util.logging.PrettyLoggable;
import edu.stanford.nlp.util.logging.PrettyLogger;
import edu.stanford.nlp.util.logging.Redwood.RedwoodChannels;

/**
 * A quadruple of ordered objects.
 * 
 * @param <T1> the type of the first element
 * @param <T2> the type of the second element
 * @param <T3> the type of the third element
 * @param <T4> the type of the fourth element
 * @author Spence Green
 */
public class Quadruple<T1,T2,T3,T4> implements Comparable<Quadruple<T1,T2,T3,T4>>, Serializable, PrettyLoggable {

  private static final long serialVersionUID = 6295043666955910662L;
  
  /** The first element. */
  public T1 first;
  /** The second element. */
  public T2 second;
  /** The third element. */
  public T3 third;
  /** The fourth element. */
  public T4 fourth;

  /**
   * Creates a quadruple of the given elements.
   *
   * @param first the first element
   * @param second the second element
   * @param third the third element
   * @param fourth the fourth element
   */
  public Quadruple(T1 first, T2 second, T3 third, T4 fourth) {
    this.first = first;
    this.second = second;
    this.third = third;
    this.fourth = fourth;
  }

  /**
   * Returns the first element.
   *
   * @return the first element
   */
  public T1 first() {
    return first;
  }

  /**
   * Returns the second element.
   *
   * @return the second element
   */
  public T2 second() {
    return second;
  }

  /**
   * Returns the third element.
   *
   * @return the third element
   */
  public T3 third() {
    return third;
  }

  /**
   * Returns the fourth element.
   *
   * @return the fourth element
   */
  public T4 fourth() {
    return fourth;
  }

  /**
   * Sets the first element.
   *
   * @param o the new first element
   */
  public void setFirst(T1 o) {
    first = o;
  }

  /**
   * Sets the second element.
   *
   * @param o the new second element
   */
  public void setSecond(T2 o) {
    second = o;
  }

  /**
   * Sets the third element.
   *
   * @param o the new third element
   */
  public void setThird(T3 o) {
    third = o;
  }
  
  /**
   * Sets the fourth element.
   *
   * @param o the new fourth element
   */
  public void setFourth(T4 o) {
    fourth = o;
  }

  @Override
  public boolean equals(Object o) {

    if (this == o) {
      return true;
    }

    if (!(o instanceof Quadruple)) {
      return false;
    }

    final Quadruple<T1,T2,T3,T4> quadruple = ErasureUtils.uncheckedCast(o);

    if (first != null ? !first.equals(quadruple.first) : quadruple.first != null) {
      return false;
    }
    if (second != null ? !second.equals(quadruple.second) : quadruple.second != null) {
      return false;
    }
    if (third != null ? !third.equals(quadruple.third) : quadruple.third != null) {
      return false;
    }
    if (fourth != null ? !fourth.equals(quadruple.fourth) : quadruple.fourth != null) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = (first != null ? first.hashCode() : 0);
    result = 29 * result + (second != null ? second.hashCode() : 0);
    result = 29 * result + (third != null ? third.hashCode() : 0);
    result = 29 * result + (fourth != null ? fourth.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "(" + first + "," + second + "," + third + "," + fourth + ")";
  }

  /**
   * Returns a Quadruple constructed from T1, T2, T3, and T4. Convenience
   * method; the compiler will disambiguate the classes used for you so that you
   * don't have to write out potentially long class names.
   *
   * @param <T1> the type of the first element
   * @param <T2> the type of the second element
   * @param <T3> the type of the third element
   * @param <T4> the type of the fourth element
   * @param t1 the first element
   * @param t2 the second element
   * @param t3 the third element
   * @param t4 the fourth element
   * @return a new Quadruple of the given elements
   */
  public static <T1, T2, T3, T4> Quadruple<T1, T2, T3, T4> makeQuadruple(T1 t1, T2 t2, T3 t3, T4 t4) {
    return new Quadruple<>(t1, t2, t3, t4);
  }

  /**
   * Returns the four elements as a new list.
   *
   * @return a list of the first, second, third and fourth elements
   */
  public List<Object> asList() {
    return CollectionUtils.makeList(first, second, third, fourth);
  }

  @SuppressWarnings("unchecked")
  @Override
  public int compareTo(Quadruple<T1, T2, T3, T4> another) {
    int comp = ((Comparable<T1>) first()).compareTo(another.first());
    if (comp != 0) {
      return comp;
    } else {
      comp = ((Comparable<T2>) second()).compareTo(another.second());
      if (comp != 0) {
        return comp;
      } else {
        comp = ((Comparable<T3>) third()).compareTo(another.third());
        if (comp != 0) {
          return comp;
        } else {
          return ((Comparable<T4>) fourth()).compareTo(another.fourth());
        }
      }
    }
  }
  
  /**
   * {@inheritDoc}
   */
  public void prettyLog(RedwoodChannels channels, String description) {
    PrettyLogger.log(channels, description, this.asList());
  }
}
