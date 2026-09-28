package edu.stanford.nlp.util;

import java.util.function.Function;

/**
 * A {@link java.util.function.Function} that is invertible, and so has the unapply method.
 * 
 *
 * @author David Hall
 * @param <T1> The domain type of the function
 * @param <T2> The range type of the function
 */
public interface BijectiveFunction<T1,T2> extends Function<T1,T2> {
  /**
   * Applies the inverse of this function.
   *
   * @param in A value in the range of this function
   * @return The value in the domain which this function maps to {@code in}
   */
  public T1 unapply(T2 in);
}
