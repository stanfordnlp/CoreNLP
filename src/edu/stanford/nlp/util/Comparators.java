package edu.stanford.nlp.util;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

// todo [cdm 2018]: Maybe much of this can now be replaced by java.util.Comparator class methods?

// Originally from edu.stanford.nlp.natlog.util
/**
 * Static utility methods for building and combining {@code Comparator}s.
 */
public class Comparators {

  private Comparators() {} // class of static methods

  /**
   * Returns a new {@code Comparator} which is the result of chaining the
   * given {@code Comparator}s.  If the first {@code Comparator}
   * considers two objects unequal, its result is returned; otherwise, the
   * result of the second {@code Comparator} is returned.  Facilitates
   * sorting on primary and secondary keys.
   *
   * @param <T> the type of objects compared
   * @param c1 the primary comparator
   * @param c2 the comparator used when {@code c1} returns 0
   * @return the chained comparator
   */
  public static <T> Comparator<T> chain(final Comparator<? super T> c1,
                                        final Comparator<? super T> c2) {
    return (o1, o2) -> {
      int x = c1.compare(o1, o2);
      return (x == 0 ? c2.compare(o1, o2) : x);
    };
  }

  /**
   * Returns a new {@code Comparator} which is the result of chaining the
   * given {@code Comparator}s.  Facilitates sorting on multiple keys.
   * The comparators are tried in list order, and the first nonzero result is
   * returned (0 if all return 0).  The list is read each time a comparison is made.
   *
   * @param <T> the type of objects compared
   * @param c the comparators to chain, in priority order
   * @return the chained comparator
   */
  public static <T> Comparator<T> chain(final List<Comparator<? super T>> c) {
    return (o1, o2) -> {
      int x = 0;
      Iterator<Comparator<? super T>> it = c.iterator();
      while (x == 0 && it.hasNext()) {
        x = it.next().compare(o1, o2);
      }
      return x;
    };
  }

  /**
   * Returns a new {@code Comparator} which is the result of chaining the
   * given {@code Comparator}s, trying them in order until one returns a nonzero result.
   *
   * @param <T> the type of objects compared
   * @param c the comparators to chain, in priority order
   * @return the chained comparator
   */
  @SafeVarargs
  public static <T> Comparator<T> chain(Comparator<T>... c) {
    return chain(Arrays.asList(c));
  }

  /**
   * Returns a new {@code Comparator} which is the reverse of the
   * given {@code Comparator}, by negating its result.
   *
   * @param <T> the type of objects compared
   * @param c the comparator to reverse
   * @return the reversed comparator
   */
  public static <T> Comparator<T> reverse(final Comparator<? super T> c) {
    return (o1, o2) -> -c.compare(o1, o2);
  }

  /**
   * Returns a comparator using the natural ordering, in which null sorts before
   * all non-null values.  See {@link #nullSafeCompare}.
   *
   * @param <T> the type of objects compared
   * @return a null-safe natural-order comparator
   */
  public static <T extends Comparable<? super T>> Comparator<T> nullSafeNaturalComparator() {
    return Comparators::nullSafeCompare;
  }

  /**
   * Returns a consistent ordering over two elements even if one of them is null
   * (as long as compareTo() is stable, of course).
   *
   * There's a "trickier" solution with xor at http://stackoverflow.com/a/481836
   * but the straightforward answer seems better.
   *
   * @param <T> the type of objects compared
   * @param one the first object, possibly null
   * @param two the second object, possibly null
   * @return 0 if both are null, negative if only {@code one} is null, positive if only
   *         {@code two} is null, and otherwise {@code one.compareTo(two)}
   */
  public static <T extends Comparable<? super T>> int nullSafeCompare(final T one, final T two) {
    if (one == null) {
      if (two == null) {
        return 0;
      }
      return -1;
    } else {
      if (two == null) {
        return 1;
      }
      return one.compareTo(two);
    }
  }

    private static <X extends Comparable<X>> int compareLists(List<? extends X> list1,
                                   List<? extends X> list2) {
      // if (list1 == null && list2 == null) return 0;  // seems better to regard all nulls as out of domain or none, not some
      if (list1 == null || list2 == null) {
        throw new IllegalArgumentException();
      }
      int size1 = list1.size();
      int size2 = list2.size();
      int size = Math.min(size1, size2);
      for (int i = 0; i < size; i++) {
        int c = list1.get(i).compareTo(list2.get(i));
        if (c != 0) return c;
      }
      return Integer.compare(size1, size2);
    }

    /**
     * Returns a comparator that orders lists lexicographically by element, with a
     * shorter list that is a prefix of a longer one sorting first.
     * It throws IllegalArgumentException if either list is null.
     *
     * @param <C> the element type of the lists
     * @return a lexicographic list comparator
     */
    public static <C extends Comparable> Comparator<List<C>> getListComparator() {
      return Comparators::compareLists;
    }

    /**
     * A {@code Comparator} that compares objects by comparing their
     * {@code String} representations, as determined by invoking
     * {@code toString()} on the objects in question.
     *
     * @return a comparator on the objects' string representations
     */
    public static Comparator getStringRepresentationComparator() {
      return Comparator.comparing(Object::toString);
    }

    /**
     * Returns a comparator that orders boolean arrays lexicographically, with
     * false before true and a shorter prefix array sorting first.
     *
     * @return a lexicographic boolean array comparator
     */
    public static Comparator<boolean[]> getBooleanArrayComparator() {
      return ArrayUtils::compareBooleanArrays;
    }

    /**
     * Returns a comparator that orders arrays lexicographically by element, with
     * a shorter prefix array sorting first.
     *
     * @param <C> the element type of the arrays
     * @return a lexicographic array comparator
     */
    public static <C extends Comparable> Comparator<C[]> getArrayComparator() {
      return ArrayUtils::compareArrays;
    }

  }
