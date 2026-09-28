package edu.stanford.nlp.util;

import edu.stanford.nlp.util.logging.PrettyLoggable;
import edu.stanford.nlp.util.logging.PrettyLogger;
import edu.stanford.nlp.util.logging.Redwood.RedwoodChannels;

import java.io.Serializable;
import java.util.List;

/**
 * A quintuple (length five) of ordered objects.
 * 
 * @param <T1> The type of the first element
 * @param <T2> The type of the second element
 * @param <T3> The type of the third element
 * @param <T4> The type of the fourth element
 * @param <T5> The type of the fifth element
 * @author Spence Green
 */
public class Quintuple<T1,T2,T3,T4, T5> implements Comparable<Quintuple<T1,T2,T3,T4,T5>>, Serializable, PrettyLoggable {

  private static final long serialVersionUID = 6295043666955910662L;

  /** The first element. */
  public T1 first;
  /** The second element. */
  public T2 second;
  /** The third element. */
  public T3 third;
  /** The fourth element. */
  public T4 fourth;
  /** The fifth element. */
  public T5 fifth;

  /**
   * Creates a Quintuple of the given elements.
   *
   * @param first The first element
   * @param second The second element
   * @param third The third element
   * @param fourth The fourth element
   * @param fifth The fifth element
   */
  public Quintuple(T1 first, T2 second, T3 third, T4 fourth, T5 fifth) {
    this.first = first;
    this.second = second;
    this.third = third;
    this.fourth = fourth;
    this.fifth = fifth;
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
   * Returns the fourth element.
   *
   * @return The fourth element
   */
  public T4 fourth() {
    return fourth;
  }

  /**
   * Returns the fifth element.
   *
   * @return The fifth element
   */
  public T5 fifth() {
    return fifth;
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
  
  /**
   * Sets the fourth element.
   *
   * @param o The new fourth element
   */
  public void setFourth(T4 o) {
    fourth = o;
  }

  /**
   * Sets the fifth element.
   *
   * @param fifth The new fifth element
   */
  public void setFifth(T5 fifth) {
    this.fifth = fifth;
  }

  @Override
  public boolean equals(Object o) {

    if (this == o) {
      return true;
    }

    if (!(o instanceof Quintuple)) {
      return false;
    }

    final Quintuple<T1,T2,T3,T4, T5> quadruple = ErasureUtils.uncheckedCast(o);

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
    if (fifth != null ? !fifth.equals(quadruple.fifth) : quadruple.fifth != null) {
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
    result = 29 * result + (fifth != null ? fifth.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "(" + first + "," + second + "," + third + "," + fourth + "," + fifth + ")";
  }

  /**
   * Returns a Quintuple constructed from T1, T2, T3, T4, and T5. Convenience
   * method; the compiler will disambiguate the classes used for you so that you
   * don't have to write out potentially long class names.
   *
   * @param <T1> The type of the first element
   * @param <T2> The type of the second element
   * @param <T3> The type of the third element
   * @param <T4> The type of the fourth element
   * @param <T5> The type of the fifth element
   * @param t1 The first element
   * @param t2 The second element
   * @param t3 The third element
   * @param t4 The fourth element
   * @param t5 The fifth element
   * @return A new Quintuple of the given elements
   */
  public static <T1, T2, T3, T4, T5> Quintuple<T1, T2, T3, T4, T5> makeQuadruple(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5) {
    return new Quintuple<>(t1, t2, t3, t4, t5);
  }

  /**
   * Returns the five elements as a list.
   *
   * @return A new list containing the five elements in order
   */
  public List<Object> asList() {
    return CollectionUtils.makeList(first, second, third, fourth, fifth);
  }

  @SuppressWarnings("unchecked")
  @Override
  public int compareTo(Quintuple<T1, T2, T3, T4, T5> another) {
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
          comp = ((Comparable<T4>) fourth()).compareTo(another.fourth());
          if(comp!=0)
            return comp;
          else
            return ((Comparable<T5>) fifth()).compareTo(another.fifth());
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
