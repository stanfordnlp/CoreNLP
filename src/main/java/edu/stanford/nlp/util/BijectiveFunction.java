package edu.stanford.nlp.util;

import java.util.function.Function;

/**
 * A {@link java.util.function.Function} that is invertible, and so has the unapply method.
 * 
 *
 * @author David Hall
 */
public interface BijectiveFunction<T1,T2> extends Function<T1,T2> {
  public T1 unapply(T2 in);
}
