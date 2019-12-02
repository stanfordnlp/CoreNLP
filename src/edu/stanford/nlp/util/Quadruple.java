package edu.stanford.nlp.util;

import java.io.Serializable;
import java.util.List;

import edu.stanford.nlp.util.logging.PrettyLoggable;
import edu.stanford.nlp.util.logging.PrettyLogger;
import edu.stanford.nlp.util.logging.Redwood.RedwoodChannels;

/**
 * A quadruple of ordered objects.
 * 
 * @author Spence Green
 */
public class Quadruple<T1,T2,T3,T4> implements Comparable<Quadruple<T1,T2,T3,T4>>, Serializable, PrettyLoggable {

  private static final long serialVersionUID = 6295043666955910662L;
  
  public T1 first;
  public T2 second;
  public T3 third;
  public T4 fourth;

  public Quadruple(T1 first, T2 second, T3 third, T4 fourth) {
    this.first = first;
    this.second = second;
    this.third = third;
    this.fourth = fourth;
  }

  public T1 first() {
    return first;
  }

  public T2 second() {
    return second;
  }

  public T3 third() {
    return third;
  }

  public T4 fourth() {
    return fourth;
  }

  public void setFirst(T1 o) {
    first = o;
  }

  public void setSecond(T2 o) {
    second = o;
  }

  public void setThird(T3 o) {
    third = o;
  }
  
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
   */
  public static <T1, T2, T3, T4> Quadruple<T1, T2, T3, T4> makeQuadruple(T1 t1, T2 t2, T3 t3, T4 t4) {
    return new Quadruple<>(t1, t2, t3, t4);
  }

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
