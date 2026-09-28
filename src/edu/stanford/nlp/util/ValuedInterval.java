package edu.stanford.nlp.util;

/**
* Interval with value
*
* @author Angel Chang
*
* @param <T> The type of the value
* @param <E> The type of the interval endpoints
*/
public class ValuedInterval<T,E extends Comparable<E>> implements HasInterval<E> {
  T value;
  Interval<E> interval;

  /**
   * Creates a valued interval.
   *
   * @param value The value
   * @param interval The interval
   */
  public ValuedInterval(T value, Interval<E> interval) {
    this.value = value;
    this.interval = interval;
  }

  /**
   * Returns the value.
   *
   * @return The value
   */
  public T getValue() {
    return value;
  }

  public Interval<E> getInterval() {
    return interval;
  }
}
