package edu.stanford.nlp.util;

import java.io.Serializable;
import java.util.Comparator;

/**
 * Represents a interval of a generic type E that is comparable.
 * An interval is an ordered pair where the first element is less
 * than the second.
 *
 * Only full intervals are currently supported
 * (i.e., both endpoints have to be specified - cannot be null).
 *
 * Provides functions for computing relationships between intervals.
 *
 * For flags that indicate relationship between two intervals, the following convention is used:
 * SS = relationship between start of first interval and start of second interval
 * SE = relationship between start of first interval and end of second interval
 * ES = relationship between end of first interval and start of second interval
 * EE = relationship between end of first interval and end of second interval
 *
 * @author Angel Chang
 */
public class Interval<E extends Comparable<E>> extends Pair<E,E> implements HasInterval<E>, Serializable {
  /**
   * Flag indicating that an interval's begin point is not inclusive
   * (by default, begin points are inclusive)
   */
  public static final int INTERVAL_OPEN_BEGIN = 0x01;
  /**
   * Flag indicating that an interval's end point is not inclusive
   * (by default, begin points are inclusive)
   */
  public static final int INTERVAL_OPEN_END = 0x02;

  private final int flags;

  /**
   * RelType gives the basic types of relations between two intervals
   */
  public enum RelType {
    /** this interval ends before the other starts */
    BEFORE,
    /** this interval starts after the other ends */
    AFTER,
    /** this interval and the other have the same endpoints */
    EQUAL,
    /** this interval's begin is the same as the other's end */
    BEGIN_MEET_END,
    /** this interval's end is the same as the other's begin */
    END_MEET_BEGIN,
    /** this interval contains the other */
    CONTAIN,
    /** this interval is inside the other */
    INSIDE,
    /** this interval and the other overlaps */
    OVERLAP,
    /**
     * The relations between the two intervals are unknown.
     * This is only used for fuzzy intervals, where not
     * all the endpoints are comparable.
     */
    UNKNOWN,
    /**
     * There is no relationship between the two interval.
     * This is used when one of the intervals being
     * compared is null.
     */
    NONE }

  protected static final int REL_FLAGS_SAME = 0x0001;
  protected static final int REL_FLAGS_BEFORE = 0x0002;
  protected static final int REL_FLAGS_AFTER = 0x0004;
  protected static final int REL_FLAGS_UNKNOWN = 0x0007;
  protected static final int REL_FLAGS_SS_SHIFT = 0;
  protected static final int REL_FLAGS_SE_SHIFT = 1*4;
  protected static final int REL_FLAGS_ES_SHIFT = 2*4;
  protected static final int REL_FLAGS_EE_SHIFT = 3*4;


  // Flags indicating how the endpoints of two intervals
  // are related

  /**
   * Both intervals have the same start point
   * <pre>
   * |---- interval 1 ----?
   * |---- interval 2 ----?
   * </pre>
   */
  public static final int REL_FLAGS_SS_SAME = 0x0001;

  /**
   * The first interval starts before the second starts
   * <pre>
   * |---- interval 1 ----?
   *     |---- interval 2 ----?
   *
   * or
   *
   * |-- interval 1 --?
   *                       |---- interval 2 ----?
   * </pre>
   */
  public static final int REL_FLAGS_SS_BEFORE = 0x0002;

  /**
   * The first interval starts after the second starts
   * <pre>
   *     |---- interval 1 ----?
   * |---- interval 2 ----?
   *
   * or
   *
   *                       |---- interval 1 ----?
   * |-- interval 2 --?
   * </pre>
   */
  public static final int REL_FLAGS_SS_AFTER = 0x0004;

  /**
   * The relationship between the start points of the
   * two intervals is unknown (used for fuzzy intervals)
   */
  public static final int REL_FLAGS_SS_UNKNOWN = 0x0007;

  /**
   * The start point of the first interval is the same
   * as the end point of the second interval
   * (the second interval is before the first)
   * <pre>
   *                     |---- interval 1 ----?
   * ?---- interval 2 ---|
   * </pre>
   */
  public static final int REL_FLAGS_SE_SAME = 0x0010;

  /**
   * The start point of the first interval is before
   * the end point of the second interval
   * (the two intervals overlap)
   * <pre>
   *                 |---- interval 1 ----?
   * ?---- interval 2 ---|
   * </pre>
   */
  public static final int REL_FLAGS_SE_BEFORE = 0x0020;

  /**
   * The start point of the first interval is after
   * the end point of the second interval
   * (the second interval is before the first)
   * <pre>
   *                      |---- interval 1 ---?
   * ?-- interval 2 ---|
   * </pre>
   */
  public static final int REL_FLAGS_SE_AFTER = 0x0040;

  /**
   * The relationship between the start point of the first
   * interval and the end point of the second interval
   * is unknown (used for fuzzy intervals)
   */
  public static final int REL_FLAGS_SE_UNKNOWN = 0x0070;

  /**
   * The end point of the first interval is the same
   * as the start point of the second interval
   * (the first interval is before the second)
   * <pre>
   * ?---- interval 1 ---|
   *                     |---- interval 2 ----?
   * </pre>
   */
  public static final int REL_FLAGS_ES_SAME = 0x0100;

  /**
   * The end point of the first interval is before
   * the start point of the second interval
   * (the first interval is before the second)
   * <pre>
   * ?-- interval 1 ---|
   *                      |---- interval 2 ---?
   * </pre>
   */
  public static final int REL_FLAGS_ES_BEFORE = 0x0200;

  /**
   * The end point of the first interval is after
   * the start point of the second interval
   * (the two intervals overlap)
   * <pre>
   * ?---- interval 1 ---|
   *                 |---- interval 2 ----?
   * </pre>
   */
  public static final int REL_FLAGS_ES_AFTER = 0x0400;

  /**
   * The relationship between the end point of the first
   * interval and the start point of the second interval
   * is unknown (used for fuzzy intervals)
   */
  public static final int REL_FLAGS_ES_UNKNOWN = 0x0700;


  /**
   * Both intervals have the same end point
   * <pre>
   * ?---- interval 1 ----|
   * ?---- interval 2 ----|
   * </pre>
   */
  public static final int REL_FLAGS_EE_SAME = 0x1000;

  /**
   * The first interval ends before the second ends
   * <pre>
   * ?---- interval 1 ----|
   *     ?---- interval 2 ----|
   *
   * or
   *
   * ?-- interval 1 --|
   *                       ?---- interval 2 ----|
   * </pre>
   */
  public static final int REL_FLAGS_EE_BEFORE = 0x2000;

  /**
   * The first interval ends after the second ends
   * <pre>
   *     ?---- interval 1 ----|
   * ?---- interval 2 ----|
   *
   * or
   *
   *                       ?---- interval 1 ----|
   * ?-- interval 2 --|
   * </pre>
   */
  public static final int REL_FLAGS_EE_AFTER = 0x4000;

  /**
   * The relationship between the end points of the
   * two intervals is unknown (used for fuzzy intervals)
   */
  public static final int REL_FLAGS_EE_UNKNOWN = 0x7000;

  // Flags indicating how two intervals are related

  /**
   * The intervals are the same (have the same start and end points).
   * When this flag is set, OVERLAP, INSIDE, and CONTAIN should also be set.
   * <pre>
   * |---- interval 1 ----|
   * |---- interval 2 ----|
   * </pre>
   */
  public static final int REL_FLAGS_INTERVAL_SAME = 0x00010000;    // SS,EE  SAME
                                                                   // Can be set with OVERLAP, INSIDE, CONTAIN
  /**
   * The first interval is entirely before the second interval
   * (the end of the first interval happens before the start of the second)
   * <pre>
   * ?---- interval 1 ----|
   *                          |---- interval 2 ----?
   * </pre>
   */
  public static final int REL_FLAGS_INTERVAL_BEFORE = 0x00020000;  // ES BEFORE => SS, SE, EE BEFORE

  /**
   * The first interval is entirely after the second interval
   * (the start of the first interval happens after the end of the second)
   * <pre>
   *                          |---- interval 1 ----?
   * ?---- interval 2 ----|
   * </pre>
   */
  public static final int REL_FLAGS_INTERVAL_AFTER = 0x00040000;   // SE AFTER => SS, ES, EE AFTER

  /**
   * The first interval overlaps with the second interval.
   */
  public static final int REL_FLAGS_INTERVAL_OVERLAP = 0x00100000; // SS SAME or AFTER, SE SAME or BEFORE
                                                                   // SS SAME or BEFORE, ES SAME or AFTER
  /**
   * The first interval is inside the second interval.
   * When this flag is set, OVERLAP should also be set.
   * <pre>
   *          |---- interval 1 ----|
   *       |---- interval 2 -----------|
   * </pre>
   */
  public static final int REL_FLAGS_INTERVAL_INSIDE = 0x00200000;  // SS SAME or AFTER, EE SAME or BEFORE

  /**
   * The first interval contains the second interval.
   * When this flag is set, OVERLAP should also be set.
   * <pre>
   *       |---- interval 1 -----------|
   *          |---- interval 2 ----|
   * </pre>
   */
  public static final int REL_FLAGS_INTERVAL_CONTAIN = 0x00400000; // SS SAME or BEFORE, EE SAME or AFTER

  /**
   * It is uncertain what the relationship between the
   * two intervals are...
   */
  public static final int REL_FLAGS_INTERVAL_UNKNOWN = 0x00770000;

  public static final int REL_FLAGS_INTERVAL_ALMOST_SAME = 0x01000000;
  public static final int REL_FLAGS_INTERVAL_ALMOST_BEFORE = 0x01000000;
  public static final int REL_FLAGS_INTERVAL_ALMOST_AFTER = 0x01000000;

//  public final static int REL_FLAGS_INTERVAL_ALMOST_OVERLAP = 0x10000000;
//  public final static int REL_FLAGS_INTERVAL_ALMOST_INSIDE = 0x20000000;
//  public final static int REL_FLAGS_INTERVAL_ALMOST_CONTAIN = 0x40000000;

  public static final int REL_FLAGS_INTERVAL_FUZZY = 0x80000000;

  protected Interval(E a, E b, int flags) {
    super(a,b);
    this.flags = flags;
    int comp = a.compareTo(b);
    if (comp > 0) {
      throw new IllegalArgumentException("Invalid interval: " + a + "," + b);
    }
  }

  /**
   * Create an interval with the specified endpoints in the specified order,
   * Returns null if a does not come before b (invalid interval)
   * @param a start endpoints
   * @param b end endpoint
   * @param <E> type of the interval endpoints
   * @return Interval with endpoints in specified order, null if a does not come before b
   */
  public static <E extends Comparable<E>> Interval<E> toInterval(E a, E b) {
    return toInterval(a,b,0);
  }

  /**
   * Create an interval with the specified endpoints in the specified order,
   * using the specified flags.  Returns null if a does not come before b
   *  (invalid interval)
   * @param a start endpoints
   * @param b end endpoint
   * @param flags flags characterizing the interval
   * @param <E> type of the interval endpoints
   * @return Interval with endpoints in specified order, null if a does not come before b
   */
  public static <E extends Comparable<E>> Interval<E> toInterval(E a, E b, int flags) {
    int comp = a.compareTo(b);
    if (comp <= 0) {
      return new Interval<E>(a,b, flags);
    } else {
      return null;
    }
  }

  /**
   * Create an interval with the specified endpoints, reordering them as needed
   * @param a one of the endpoints
   * @param b the other endpoint
   * @param <E> type of the interval endpoints
   * @return Interval with endpoints re-ordered as needed
   */
  public static <E extends Comparable<E>> Interval<E> toValidInterval(E a, E b) {
    return toValidInterval(a,b,0);
  }

  /**
   * Create an interval with the specified endpoints, reordering them as needed,
   * using the specified flags
   * @param a one of the endpoints
   * @param b the other endpoint
   * @param flags flags characterizing the interval
   * @param <E> type of the interval endpoints
   * @return Interval with endpoints re-ordered as needed
   */
  public static <E extends Comparable<E>> Interval<E> toValidInterval(E a, E b, int flags) {
    int comp = a.compareTo(b);
    if (comp <= 0) {
      return new Interval<E>(a,b,flags);
    } else {
      return new Interval<E>(b,a,flags);
    }
  }

  /**
   * Returns this interval.
   * @return this interval
   */
  @Override
  public Interval<E> getInterval() {
    return this;
  }

  /**
   * Returns the start point.
   * @return the start point of this interval
   */
  public E getBegin()
  {
    return first;
  }

  /**
   * Returns the end point.
   * @return the end point of this interval
   */
  public E getEnd()
  {
    return second;
  }

  protected static <E extends Comparable<E>> E max(E a, E b)
  {
    int comp = a.compareTo(b);
    return (comp > 0)? a:b;
  }

  protected static <E extends Comparable<E>> E min(E a, E b)
  {
    int comp = a.compareTo(b);
    return (comp < 0)? a:b;
  }

  /**
   * Checks whether the point p is contained inside this interval
   * @param p point to check
   * @return True if the point p is contained withing the interval, false otherwise
   */
  public boolean contains(E p)
  {
    // Check that the start point is before p
    boolean check1 = (includesBegin())? (first.compareTo(p) <= 0):(first.compareTo(p) < 0);
    // Check that the end point is after p
    boolean check2 = (includesEnd())? (second.compareTo(p) >= 0):(second.compareTo(p) > 0);
    return (check1 && check2);
  }

  public boolean containsOpen(E p)
  {
    // Check that the start point is before p
    boolean check1 = first.compareTo(p) <= 0;
    // Check that the end point is after p
    boolean check2 = second.compareTo(p) >= 0;
    return (check1 && check2);
  }

  public boolean contains(Interval<E> other)
  {
    boolean containsOtherBegin = (other.includesBegin())? contains(other.getBegin()): containsOpen(other.getBegin());
    boolean containsOtherEnd = (other.includesEnd())? contains(other.getEnd()): containsOpen(other.getEnd());
    return (containsOtherBegin && containsOtherEnd);
  }

    /**
    * Returns (smallest) interval that contains both this and the other interval
    * @param other - Other interval to include
    * @return Smallest interval that contains both this and the other interval
    */
  public Interval expand(Interval<E> other)
  {
    if (other == null) return this;
    E a = min(this.first, other.first);
    E b = max(this.second, other.second);
    return toInterval(a,b);
  }

  /**
   * Returns interval that is the intersection of this and the other interval
   * Returns null if intersect is null
   * @param other interval with which to intersect
   * @return interval that is the intersection of this and the other interval
   */
  public Interval intersect(Interval<E> other)
  {
    if (other == null) return null;
    E a = max(this.first, other.first);
    E b = min(this.second, other.second);
    return toInterval(a,b);
  }

  /**
   * Check whether this interval overlaps with the other interval
   * (i.e. the intersect would not be null)
   * @param other interval to compare with
   * @return true if this interval overlaps the other interval
   */
  public boolean overlaps(Interval<E> other)
  {
    if (other == null) return false;
    int comp12 = this.first.compareTo(other.second());
    int comp21 = this.second.compareTo(other.first());
    if (comp12 > 0 || comp21 < 0) {
      return false;
    } else {
      if (comp12 == 0) {
        if (!this.includesBegin() || !other.includesEnd()) {
          return false;
        }
      }
      if (comp21 == 0) {
        if (!this.includesEnd() || !other.includesBegin()) {
          return false;
        }
      }
      return true;
    }
  }

  /**
   * Returns whether the start endpoint is included in the interval
   * @return true if the start endpoint is included in the interval
   */
  public boolean includesBegin()
  {
    return ((flags & INTERVAL_OPEN_BEGIN) == 0);
  }

  /**
   * Returns whether the end endpoint is included in the interval
   * @return true if the end endpoint is included in the interval
   */
  public boolean includesEnd()
  {
    return ((flags & INTERVAL_OPEN_END) == 0);
  }

/*  // Returns true if end before (start of other)
  public boolean isEndBeforeBegin(Interval<E> other)
  {
    if (other == null) return false;
    int comp21 = this.second.compareTo(other.first());
    return (comp21 < 0);
  }

  // Returns true if end before or eq (start of other)
  public boolean isEndBeforeEqBegin(Interval<E> other)
  {
    if (other == null) return false;
    int comp21 = this.second.compareTo(other.first());
    return (comp21 <= 0);
  }

  // Returns true if end before or eq (start of other)
  public boolean isEndEqBegin(Interval<E> other)
  {
    if (other == null) return false;
    int comp21 = this.second.compareTo(other.first());
    return (comp21 == 0);
  }

  // Returns true if start after (end of other)
  public boolean isBeginAfterEnd(Interval<E> other)
  {
    if (other == null) return false;
    int comp12 = this.first.compareTo(other.second());
    return (comp12 > 0);
  }

  // Returns true if start eq(end of other)
  public boolean isBeginAfterEqEnd(Interval<E> other)
  {
    if (other == null) return false;
    int comp12 = this.first.compareTo(other.second());
    return (comp12 >= 0);
  }

  // Returns true if start eq(end of other)
  public boolean isBeginEqEnd(Interval<E> other)
  {
    if (other == null) return false;
    int comp12 = this.first.compareTo(other.second());
    return (comp12 >= 0);
  }

  // Returns true if start is the same
  public boolean isBeginSame(Interval<E> other)
  {
    if (other == null) return false;
    int comp11 = this.first.compareTo(other.first());
    return (comp11 == 0);
  }

  // Returns true if end is the same
  public boolean isEndSame(Interval<E> other)
  {
    if (other == null) return false;
    int comp22 = this.second.compareTo(other.second());
    return (comp22 == 0);
  } */

  /**
   * Checks whether this interval is comparable with another interval
   *  comes before or after
   * @param other interval to compare with
   */
  public boolean isIntervalComparable(Interval<E> other)
  {
    int flags = getRelationFlags(other);
    if (checkMultipleBitSet(flags & REL_FLAGS_INTERVAL_UNKNOWN)) {
      return false;
    }
    return checkFlagSet(flags, REL_FLAGS_INTERVAL_BEFORE) || checkFlagSet(flags, REL_FLAGS_INTERVAL_AFTER);
  }

  /**
   * Returns order of another interval compared to this one
   * @param other Interval to compare with
   * @return -1 if this interval is before the other interval, 1 if this interval is after
   *         0 otherwise (may indicate the two intervals are same or not comparable)
   */
  public int compareIntervalOrder(Interval<E> other)
  {
    int flags = getRelationFlags(other);
    if (checkFlagExclusiveSet(flags, REL_FLAGS_INTERVAL_BEFORE, REL_FLAGS_INTERVAL_UNKNOWN)) {
      return -1;
    } else if (checkFlagExclusiveSet(flags, REL_FLAGS_INTERVAL_AFTER, REL_FLAGS_INTERVAL_UNKNOWN)) {
      return 1;
    } else {
      return 0;
    }
  }

  protected static int toRelFlags(int comp, int shift)
  {
    int flags = 0;
    if (comp == 0) {
      flags = REL_FLAGS_SAME;
    } else if (comp > 0) {
      flags = REL_FLAGS_AFTER;
    } else {
      flags = REL_FLAGS_BEFORE;
    }
    flags = flags << shift;
    return flags;
  }

  /**
   * Return set of flags indicating possible relationships between
   *  this interval and another interval.
   *
   * @param other Interval with which to compare with
   * @return flags indicating possible relationship between this interval and the other interval
   */
  public int getRelationFlags(Interval<E> other)
  {
    if (other == null) return 0;
    int flags = 0;
    int comp11 = this.first.compareTo(other.first());   // 3 choices
    flags |= toRelFlags(comp11, REL_FLAGS_SS_SHIFT);
    int comp22 = this.second.compareTo(other.second());   // 3 choices
    flags |= toRelFlags(comp22, REL_FLAGS_EE_SHIFT);
    int comp12 = this.first.compareTo(other.second());   // 3 choices
    flags |= toRelFlags(comp12, REL_FLAGS_SE_SHIFT);
    int comp21 = this.second.compareTo(other.first());   // 3 choices
    flags |= toRelFlags(comp21, REL_FLAGS_ES_SHIFT);
    flags = addIntervalRelationFlags(flags, false);
    return flags;
  }

  protected static int addIntervalRelationFlags(int flags, boolean checkFuzzy) {
    int f11 = extractRelationSubflags(flags, REL_FLAGS_SS_SHIFT);
    int f22 = extractRelationSubflags(flags, REL_FLAGS_EE_SHIFT);
    int f12 = extractRelationSubflags(flags, REL_FLAGS_SE_SHIFT);
    int f21 = extractRelationSubflags(flags, REL_FLAGS_ES_SHIFT);
    if (checkFuzzy) {
      boolean isFuzzy = checkMultipleBitSet(f11) || checkMultipleBitSet(f12) || checkMultipleBitSet(f21) || checkMultipleBitSet(f22);
      if (isFuzzy) {
        flags |= REL_FLAGS_INTERVAL_FUZZY;
      }
    }
    if (((f11 & REL_FLAGS_SAME) != 0) && ((f22 & REL_FLAGS_SAME) != 0)) {
      // SS,EE SAME
      flags |= REL_FLAGS_INTERVAL_SAME;  // Possible
    }
    if (((f21 & REL_FLAGS_BEFORE) != 0)) {
      // ES BEFORE => SS, SE, EE BEFORE
      flags |= REL_FLAGS_INTERVAL_BEFORE;  // Possible
    }
    if (((f12 & REL_FLAGS_AFTER) != 0)) {
      // SE AFTER => SS, ES, EE AFTER
      flags |= REL_FLAGS_INTERVAL_AFTER;  // Possible
    }
    if (((f11 & (REL_FLAGS_SAME | REL_FLAGS_AFTER)) != 0) && ((f12 & (REL_FLAGS_SAME | REL_FLAGS_BEFORE)) != 0)) {
      // SS SAME or AFTER, SE SAME or BEFORE
      //     |-----|
      // |------|
      flags |= REL_FLAGS_INTERVAL_OVERLAP;  // Possible
    }
    if (((f11 & (REL_FLAGS_SAME | REL_FLAGS_BEFORE)) != 0) && ((f21 & (REL_FLAGS_SAME | REL_FLAGS_AFTER)) != 0)) {
      // SS SAME or BEFORE, ES SAME or AFTER
      // |------|
      //     |-----|
      flags |= REL_FLAGS_INTERVAL_OVERLAP;  // Possible
    }
    if (((f11 & (REL_FLAGS_SAME | REL_FLAGS_AFTER)) != 0) && ((f22 & (REL_FLAGS_SAME | REL_FLAGS_BEFORE)) != 0)) {
      // SS SAME or AFTER, EE SAME or BEFORE
      //     |------|
      // |---------------|
      flags |= REL_FLAGS_INTERVAL_INSIDE;  // Possible
    }
    if (((f11 & (REL_FLAGS_SAME | REL_FLAGS_BEFORE)) != 0) && ((f22 & (REL_FLAGS_SAME | REL_FLAGS_AFTER)) != 0)) {
      // SS SAME or BEFORE, EE SAME or AFTER
      flags |= REL_FLAGS_INTERVAL_CONTAIN;  // Possible
      // |---------------|
      //     |------|
    }
    return flags;
  }

  public static int extractRelationSubflags(int flags, int shift)
  {
    return (flags >> shift) & 0xf;
  }

  /**
   * Utility function to check if multiple bits are set for flags
   * @param flags flags to check
   * @return true if multiple bits are set
   */
  public static boolean checkMultipleBitSet(int flags) {
    boolean set = false;
    while (flags != 0) {
      if ((flags & 0x01) != 0) {
        if (set) { return false; }
        else { set = true; }
      }
      flags = flags >> 1;
    }
    return false;
  }

  /**
   * Utility function to check if a particular flag is set
   *   given a particular set of flags.
   * @param flags flags to check
   * @param flag bit for flag of interest (is this flag set or not)
   * @return true if flag is set for flags
   */
  public static boolean checkFlagSet(int flags, int flag)
  {
    return ((flags & flag) != 0);
  }

  /**
   * Utility function to check if a particular flag is set exclusively
   *   given a particular set of flags and a mask.
   * @param flags flags to check
   * @param flag bit for flag of interest (is this flag set or not)
   * @param mask bitmask of bits to check
   * @return true if flag is exclusively set for flags & mask
   */
  public static boolean checkFlagExclusiveSet(int flags, int flag, int mask)
  {
    int f = flags & flag;
    if (f != 0) {
      return (flags & mask & ~flag) == 0;
    } else {
      return false;
    }
  }

  /**
   * Returns the relationship of this interval to the other interval
   * The most specific relationship from the following is returned.
   *
   * NONE: the other interval is null
   * EQUAL: this have same endpoints as other
   * OVERLAP:  this and other overlaps
   * BEFORE: this ends before other starts
   * AFTER: this starts after other ends
   * BEGIN_MEET_END: this begin is the same as the others end
   * END_MEET_BEGIN: this end is the same as the others begin
   * CONTAIN: this contains the other
   * INSIDE: this is inside the other
   *
   * UNKNOWN: this is returned if for some reason it is not
   *          possible to determine the exact relationship
   *          of the two intervals (possible for fuzzy intervals)
   * @param other The other interval with which to compare with
   * @return RelType indicating relationship between the two interval
   */
  public RelType getRelation(Interval<E> other) {
    // TODO: Handle open/closed intervals?
    if (other == null) return RelType.NONE;
    int comp11 = this.first.compareTo(other.first());   // 3 choices
    int comp22 = this.second.compareTo(other.second());   // 3 choices

    if (comp11 == 0) {
      if (comp22 == 0) {
        // |---|  this
        // |---|   other
        return RelType.EQUAL;
      } if (comp22 < 0) {
        // SAME START - this finishes before other
        // |---|  this
        // |------|   other
        return RelType.INSIDE;
      } else {
        // SAME START - this finishes after other
        // |------|  this
        // |---|   other
        return RelType.CONTAIN;
      }
    } else if (comp22 == 0) {
      if (comp11 < 0) {
        // SAME FINISH - this start before other
        // |------|  this
        //    |---|   other
        return RelType.CONTAIN;
      } else /*if (comp11 > 0) */ {
        // SAME FINISH - this starts after other
        //    |---|  this
        // |------|   other
        return RelType.INSIDE;
      }
    } else if (comp11 > 0 && comp22 < 0) {
      //    |---|  this
      // |---------|   other
      return RelType.INSIDE;
    } else if (comp11 < 0 && comp22 > 0) {
      // |---------|  this
      //    |---|   other
      return RelType.CONTAIN;
    } else {
      int comp12 = this.first.compareTo(other.second());
      int comp21 = this.second.compareTo(other.first());
      if (comp12 > 0) {
        //           |---|  this
        // |---|   other
        return RelType.AFTER;
      } else if (comp21 < 0) {
        // |---|  this
        //        |---|   other
        return RelType.BEFORE;
      } else if (comp12 == 0) {
        //     |---|  this
        // |---|   other
        return RelType.BEGIN_MEET_END;
      } else if (comp21 == 0) {
        // |---|  this
        //     |---|   other
        return RelType.END_MEET_BEGIN;
      } else {
        return RelType.OVERLAP;
      }
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }

    Interval interval = (Interval) o;

    if (flags != interval.flags) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + flags;
    return result;
  }

  public static double getMidPoint(Interval<Integer> interval) {
    return (interval.getBegin() + interval.getEnd())/2.0;
  }

  public static double getRadius(Interval<Integer> interval) {
    return (interval.getEnd() - interval.getBegin())/2.0;
  }

  @SuppressWarnings("unchecked")
  public static <T extends HasInterval<Integer>> Comparator<T> lengthEndpointsComparator() {
    return ErasureUtils.uncheckedCast(HasInterval.LENGTH_ENDPOINTS_COMPARATOR);
  }

  @SuppressWarnings("unchecked")
  public static <T extends HasInterval<Integer>> Function<T, Double> lengthScorer() {
    return ErasureUtils.uncheckedCast(LENGTH_SCORER);
  }

  public static final Function<HasInterval<Integer>, Double> LENGTH_SCORER = new Function<HasInterval<Integer>,Double>() {
    @Override
    public Double apply(HasInterval<Integer> in) {
      Interval<Integer> interval = in.getInterval();
      return (double) (interval.getEnd() - interval.getBegin());
    }
  };

  private static final long serialVersionUID = 1;
}
