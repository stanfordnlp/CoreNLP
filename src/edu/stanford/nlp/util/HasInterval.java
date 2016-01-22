package edu.stanford.nlp.util;

import java.util.Comparator;

/**
 * HasInterval interface
 *
 * @author Angel Chang
 */
public interface HasInterval<E extends Comparable<E>> {
  /**
   * Returns the interval
   * @return interval
   */
  public Interval<E> getInterval();

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

  public final static Comparator<HasInterval> ENDPOINTS_COMPARATOR =
      (e1, e2) -> (e1.getInterval().compareTo(e2.getInterval()));

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

  public final static Comparator<HasInterval<Integer>> LENGTH_ENDPOINTS_COMPARATOR =
          Comparators.chain(HasInterval.LENGTH_GT_COMPARATOR, HasInterval.ENDPOINTS_COMPARATOR);

}
