package edu.stanford.nlp.process;


import edu.stanford.nlp.util.Function;


import edu.stanford.nlp.util.Filter;

/**
 * Filter that first transforms input before filtering it. An object is
 * accepted iff the given filter accepts its output from the given Function.
 * This is particularly useful when filtering a list of objects, since for
 * example the Function might return a single field or method result from
 * the objects being filtered, and thus the Object could be filtered based
 * on some part of it without having to write a special filter.
 *
 * @author Christopher Manning
 */
public class TransformedFilter {

  private TransformedFilter() {}
  
  /**
   * Filter that first transforms input before filtering it. An object is
   * accepted iff the given filter accepts its output from the given Function.
   * This is particularly useful when filtering a list of objects, since for
   * example the Function might return a single field or method result from
   * the objects being filtered, and thus the Object could be filtered based
   * on some part of it without having to write a special filter.
   */
  public static <IN,OUT> Filter<IN> filter(Filter<OUT> filter, Function<IN,OUT> func) {
    return (new TransformedFilterImpl<IN,OUT>(filter, func));
  }

  /**
   * Filters on output of Function, returns matching input.
   */
  private static class TransformedFilterImpl<IN,OUT> implements Filter<IN> {
    /**
     * 
     */
    private static final long serialVersionUID = 4256464879154358772L;
    private Filter<OUT> filter;
    private Function<IN,OUT> func;

    public TransformedFilterImpl(Filter<OUT> filter, Function<IN,OUT> func) {
      this.filter = filter;
      this.func = func;
    }

    /**
     * Returns o iff filter accepts func's transformation of o.
     */
    public boolean accept(IN o) {
      return (filter.accept(func.apply(o)));
    }
  }

}
