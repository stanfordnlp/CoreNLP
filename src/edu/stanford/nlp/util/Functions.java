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
   * Returns the {@link Function} {@code g o f}.
   * Note that the arguments are passed to the composed function's constructor
   * in swapped order, so the returned function actually computes
   * {@code f.apply(g.apply(x))}, which only works if the types happen to line up.
   *
   * @param <T1> the input type of {@code f}
   * @param <T2> the output type of {@code f} and input type of {@code g}
   * @param <T3> the output type of {@code g}
   * @param f the function meant to be applied first
   * @param g the function meant to be applied second
   * @return g o f (but see the note above)
   */
  @SuppressWarnings("unchecked") // Type system is stupid
  public static <T1,T2,T3> Function<T1,T3> compose(Function<T1,T2> f,Function<? super T2,T3> g) {
    return new ComposedFunction(f,g);
  }

  /**
   * Returns a function which returns its argument unchanged.
   *
   * @param <T> the type of the argument
   * @return the identity function
   */
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

  /**
   * Returns the inverse of a bijective function, whose {@code apply} and
   * {@code unapply} are those of {@code f} swapped.  If {@code f} is itself
   * the result of {@code invert}, the original function is returned.
   *
   * @param <T1> the domain of {@code f}
   * @param <T2> the range of {@code f}
   * @param f the function to invert
   * @return the inverse of {@code f}
   */
  public static <T1,T2> BijectiveFunction<T2,T1> invert(BijectiveFunction<T1,T2> f) {
    if( f instanceof InvertedBijection) {
      return ((InvertedBijection<T2, T1>)f).f;
    }
    return new InvertedBijection<>(f);
  }

}
