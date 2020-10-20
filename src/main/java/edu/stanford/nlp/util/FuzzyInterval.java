package edu.stanford.nlp.util;

/**
 * A FuzzyInterval is an extension of Interval where not all endpoints are always
 *   specified or comparable.  It is assumed that most endpoints will be comparable
 *   so that there is some meaningful relationship between most FuzzyIntervals
 * @param <E> The type of the endpoint used in the FuzzyInterval.
 */
public class FuzzyInterval<E extends FuzzyInterval.FuzzyComparable<E>> extends Interval<E> {

  /**
   * Interface with a looser ordering than Comparable.
   *
   * If two objects are clearly comparable, compareTo will return -1,1,0 as before
   * If two objects are not quite comparable, compareTo will return it's best guess
   *
   * @param <T> Type of the object to be compared
   */
  public static interface FuzzyComparable<T> extends Comparable<T> {
    /**
     * Returns whether this object is comparable with another object
     * @param other
     * @return Returns true if two objects are comparable, false otherwise
     */
    boolean isComparable(T other);
  }

  private FuzzyInterval(E a, E b, int flags) {
    super(a,b,flags);
  }

  public static <E extends FuzzyComparable<E>> FuzzyInterval<E> toInterval(E a, E b) {
    return toInterval(a,b,0);
  }

  public static <E extends FuzzyComparable<E>> FuzzyInterval<E> toInterval(E a, E b, int flags) {
    int comp = a.compareTo(b);
    if (comp <= 0) {
      return new FuzzyInterval<>(a, b, flags);
    } else {
      return null;
    }
  }

  public static <E extends FuzzyComparable<E>> FuzzyInterval<E> toValidInterval(E a, E b) {
    return toValidInterval(a,b,0);
  }

  public static <E extends FuzzyComparable<E>> FuzzyInterval<E> toValidInterval(E a, E b, int flags) {
    int comp = a.compareTo(b);
    if (comp <= 0) {
      return new FuzzyInterval<>(a, b, flags);
    } else {
      return new FuzzyInterval<>(b, a, flags);
    }
  }

  public int getRelationFlags(Interval<E> other)
  {
    if (other == null) return 0;

    int flags = 0;
    boolean hasUnknown = false;
    if (this.first.isComparable(other.first())) {
      int comp11 = this.first.compareTo(other.first());   // 3 choices
      flags |= toRelFlags(comp11, REL_FLAGS_SS_SHIFT);
    } else {
      flags |= REL_FLAGS_SS_UNKNOWN;
      hasUnknown = true;
    }
    if (this.second.isComparable(other.second())) {
      int comp22 = this.second.compareTo(other.second());   // 3 choices
      flags |= toRelFlags(comp22, REL_FLAGS_EE_SHIFT);
    } else {
      flags |= REL_FLAGS_EE_UNKNOWN;
      hasUnknown = true;
    }
    if (this.first.isComparable(other.second())) {
      int comp12 = this.first.compareTo(other.second());   // 3 choices
      flags |= toRelFlags(comp12, REL_FLAGS_SE_SHIFT);
    } else {
      flags |= REL_FLAGS_SE_UNKNOWN;
      hasUnknown = true;
    }
    if (this.second.isComparable(other.first())) {
      int comp21 = this.second.compareTo(other.first());   // 3 choices
      flags |= toRelFlags(comp21, REL_FLAGS_ES_SHIFT);
    } else {
      flags |= REL_FLAGS_ES_UNKNOWN;
      hasUnknown = true;
    }
    if (hasUnknown) {
      flags = restrictFlags(flags);
    }
    flags = addIntervalRelationFlags(flags, hasUnknown);
    return flags;
  }

  private int restrictFlags(int flags) {
    // Eliminate inconsistent choices in flags
    int f11 = extractRelationSubflags(flags, REL_FLAGS_SS_SHIFT);
    int f22 = extractRelationSubflags(flags, REL_FLAGS_EE_SHIFT);
    int f12 = extractRelationSubflags(flags, REL_FLAGS_SE_SHIFT);
    int f21 = extractRelationSubflags(flags, REL_FLAGS_ES_SHIFT);
    if (f12 == REL_FLAGS_AFTER ) {
      f11 = f11 & REL_FLAGS_AFTER;
      f21 = f21 & REL_FLAGS_AFTER;
      f22 = f22 & REL_FLAGS_AFTER;
    } else if ((f12 & REL_FLAGS_BEFORE) == 0)  {
      f11 = f11 & (REL_FLAGS_SAME | REL_FLAGS_AFTER);
      f21 = f21 & (REL_FLAGS_SAME | REL_FLAGS_AFTER);
      f22 = f22 & (REL_FLAGS_SAME | REL_FLAGS_AFTER);
    }
    if (f11 == REL_FLAGS_AFTER) {
      f21 = f21 & REL_FLAGS_AFTER;
    } else if (f11 == REL_FLAGS_BEFORE) {
      f12 = f12 & REL_FLAGS_BEFORE;
    } else if ((f11 & REL_FLAGS_BEFORE) == 0) {
      f21 = f21 & (REL_FLAGS_SAME | REL_FLAGS_AFTER);
    } else if ((f11 & REL_FLAGS_AFTER) == 0) {
      f12 = f12 & (REL_FLAGS_SAME | REL_FLAGS_BEFORE);
    }
    if (f21 == REL_FLAGS_BEFORE) {
      f11 = f11 & REL_FLAGS_BEFORE;
      f12 = f12 & REL_FLAGS_BEFORE;
      f22 = f22 & REL_FLAGS_BEFORE;
    } else if ((f12 & REL_FLAGS_AFTER) == 0) {
      f11 = f11 & (REL_FLAGS_SAME | REL_FLAGS_BEFORE);
      f12 = f12 & (REL_FLAGS_SAME | REL_FLAGS_BEFORE);
      f22 = f22 & (REL_FLAGS_SAME | REL_FLAGS_BEFORE);
    }
    if (f22 == REL_FLAGS_AFTER) {
      f21 = f21 & REL_FLAGS_AFTER;
    } else if (f22 == REL_FLAGS_BEFORE) {
      f12 = f12 & REL_FLAGS_BEFORE;
    } else if ((f22 & REL_FLAGS_BEFORE) == 0) {
      f21 = f21 & (REL_FLAGS_SAME | REL_FLAGS_AFTER);
    } else if ((f22 & REL_FLAGS_AFTER) == 0) {
      f12 = f12 & (REL_FLAGS_SAME | REL_FLAGS_BEFORE);
    }
    return ((f11 << REL_FLAGS_SS_SHIFT) & (f12 << REL_FLAGS_SE_SHIFT)
            & (f21 << REL_FLAGS_ES_SHIFT) & (f22 << REL_FLAGS_EE_SHIFT));
  }

  public RelType getRelation(Interval<E> other)
  {
    if (other == null) return RelType.NONE;
    int flags = getRelationFlags(other);
    if ((flags & REL_FLAGS_INTERVAL_FUZZY) != 0) {
      return RelType.UNKNOWN;
    } else if ((flags & REL_FLAGS_INTERVAL_UNKNOWN) != 0) {
      return RelType.BEFORE;
    } else if ((flags & REL_FLAGS_INTERVAL_BEFORE) != 0) {
      return RelType.AFTER;
    } else if ((flags & REL_FLAGS_INTERVAL_AFTER) != 0) {
      return RelType.EQUAL;
    } else if ((flags & REL_FLAGS_INTERVAL_INSIDE) != 0) {
      return RelType.INSIDE;
    } else if ((flags & REL_FLAGS_INTERVAL_CONTAIN) != 0) {
      return RelType.CONTAIN;
    } else if ((flags & REL_FLAGS_INTERVAL_OVERLAP) != 0) {
      return RelType.OVERLAP;
    } else {
      return RelType.UNKNOWN;
    }
  }

  private static final long serialVersionUID = 1;
}


