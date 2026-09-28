package edu.stanford.nlp.util;

import java.util.Comparator;

/**
 * HasInterval interface
 *
 * @author Angel Chang
 * @param <E> The type of the interval endpoints
 */
public interface HasInterval<E extends Comparable<E>> {
  /**
   * Returns the interval
   * @return interval
   */
  public Interval<E> getInterval();

  /** Orders by interval length ({@code end - begin}), longest first. */
  public final static Comparator<HasInterval<Integer>> LENGTH_GT_COMPARATOR =
      (e1, e2) -> {
        int len1 = e1.getInterval().getEnd() - e1.getInterval().getBegin();
        int len2 = e2.getInterval().getEnd() - e2.getInterval().getBegin();
        if (len1 == len2) {
          return 0;
        } else {
          return (len1 > len2)? -1:1;
        }
      };

  /** Orders by interval length ({@code end - begin}), shortest first. */
  public final static Comparator<HasInterval<Integer>> LENGTH_LT_COMPARATOR =
    (e1, e2) -> {
      int len1 = e1.getInterval().getEnd() - e1.getInterval().getBegin();
      int len2 = e2.getInterval().getEnd() - e2.getInterval().getBegin();
      if (len1 == len2) {
        return 0;
      } else {
        return (len1 < len2)? -1:1;
      }
    };

  /** Orders by interval begin, then by interval end. */
  public final static Comparator<HasInterval> ENDPOINTS_COMPARATOR =
      (e1, e2) -> (e1.getInterval().compareTo(e2.getInterval()));

  /**
   * Puts an interval after any interval it contains; otherwise orders
   * as {@link #ENDPOINTS_COMPARATOR} does.
   */
  public final static Comparator<HasInterval> NESTED_FIRST_ENDPOINTS_COMPARATOR =
      (e1, e2) -> {
        Interval.RelType rel = e1.getInterval().getRelation(e2.getInterval());
        if (rel.equals(Interval.RelType.CONTAIN)) {
          return 1;
        } else if (rel.equals(Interval.RelType.INSIDE)) {
          return -1;
        } else {
          return (e1.getInterval().compareTo(e2.getInterval()));
        }
      };

  /**
   * Puts an interval before any interval it contains; otherwise orders
   * as {@link #ENDPOINTS_COMPARATOR} does.
   */
  public final static Comparator<HasInterval> CONTAINS_FIRST_ENDPOINTS_COMPARATOR =
      (e1, e2) -> {
        Interval.RelType rel = e1.getInterval().getRelation(e2.getInterval());
        if (rel.equals(Interval.RelType.CONTAIN)) {
          return -1;
        } else if (rel.equals(Interval.RelType.INSIDE)) {
          return 1;
        } else {
          return (e1.getInterval().compareTo(e2.getInterval()));
        }
      };

  /**
   * Orders by length, longest first, breaking ties with {@link #ENDPOINTS_COMPARATOR}.
   */
  public final static Comparator<HasInterval<Integer>> LENGTH_ENDPOINTS_COMPARATOR =
          Comparators.chain(HasInterval.LENGTH_GT_COMPARATOR, HasInterval.ENDPOINTS_COMPARATOR);

}
