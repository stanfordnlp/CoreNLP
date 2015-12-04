package edu.stanford.nlp.util;

import java.util.function.Function;

/**
 * Utility code for {@link java.util.function.Function}.
 * 
 * @author Roger Levy (rog@stanford.edu)
 * @author javanlp
 */
public class Functions {

  private Functions() {}

  private static class ComposedFunction<T1,T2,T3> implements Function<T1,T3> {
    Function<? super T2,T3> g;
    Function<T1,T2> f;

    public ComposedFunction(Function<? super T2, T3> g, Function<T1, T2> f) {
      this.g = g;
      this.f = f;
    }

    public T3 apply(T1 t1) {
      return(g.apply(f.apply(t1)));
    }
  }

  /**
   * Returns the {@link Function} <tt>g o f</tt>.
   * @return g o f
   */
  @SuppressWarnings("unchecked") // Type system is stupid
  public static <T1,T2,T3> Function<T1,T3> compose(Function<T1,T2> f,Function<? super T2,T3> g) {
    return new ComposedFunction(f,g);
  }

  public static <T> Function<T,T> identityFunction() {
    return t -> t;
  }

  private static class InvertedBijection<T1,T2> implements BijectiveFunction<T2,T1> {
    InvertedBijection(BijectiveFunction<T1,T2> f) {
      this.f = f;
    }

    private final BijectiveFunction<T1,T2> f;

    public T1 apply(T2 in) {
      return f.unapply(in);
    }

    public T2 unapply(T1 in) {
      return f.apply(in);
    }
  }

  public static <T1,T2> BijectiveFunction<T2,T1> invert(BijectiveFunction<T1,T2> f) {
    if( f instanceof InvertedBijection) {
      return ((InvertedBijection<T2, T1>)f).f;
    }
    return new InvertedBijection<>(f);
  }

}
