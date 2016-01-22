package edu.stanford.nlp.util;

/**
* Interval with value
*
* @author Angel Chang
*/
public class ValuedInterval<T,E extends Comparable<E>> implements HasInterval<E> {
  T value;
  Interval<E> interval;

  public ValuedInterval(T value, Interval<E> interval) {
    this.value = value;
    this.interval = interval;
  }

  public T getValue() {
    return value;
  }

  public Interval<E> getInterval() {
    return interval;
  }
}
