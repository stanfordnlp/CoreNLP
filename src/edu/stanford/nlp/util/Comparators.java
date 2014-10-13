package edu.stanford.nlp.util;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;


  // Originally from edu.stanford.nlp.natlog.util
public class Comparators {

  private Comparators() {} // class of static methods

  /**
   * Returns a new {@code Comparator} which is the result of chaining the
   * given {@code Comparator}s.  If the first <code>Comparator</code>
   * considers two objects unequal, its result is returned; otherwise, the
   * result of the second <code>Comparator</code> is returned.  Facilitates
   * sorting on primary and secondary keys.
   */
  public static <T> Comparator<T> chain(final Comparator<? super T> c1,
                                        final Comparator<? super T> c2) {
    return (o1, o2) -> {
      int x = c1.compare(o1, o2);
      return (x == 0 ? c2.compare(o1, o2) : x);
    };
  }

  /**
   * Returns a new <code>Comparator</code> which is the result of chaining the
   * given <code>Comparator</code>s.  Facilitates sorting on multiple keys.
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

  @SafeVarargs
  public static <T> Comparator<T> chain(Comparator<? super T>... c) {
    return chain(Arrays.asList(c));
  }

  /**
   * Returns a new <code>Comparator</code> which is the reverse of the
   * given <code>Comparator</code>.
   */
  public static <T> Comparator<T> reverse(final Comparator<? super T> c) {
    return (o1, o2) -> -c.compare(o1, o2);
  }

  public static <T extends Comparable<? super T>> Comparator<T> nullSafeNaturalComparator() {
    return Comparators::nullSafeCompare;
  }

  /**
   * Returns a consistent ordering over two elements even if one of them is null
   * (as long as compareTo() is stable, of course).
   *
   * There's a "trickier" solution with xor at http://stackoverflow.com/a/481836
   * but the straightforward answer seems better.
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
      if (size1 < size2) return -1;
      if (size1 > size2) return 1;
      return 0;
    }

    public static <C extends Comparable> Comparator<List<C>> getListComparator() {
      return (list1, list2) -> compareLists(list1, list2);
    }

    /**
     * A <code>Comparator</code> that compares objects by comparing their
     * <code>String</code> representations, as determined by invoking
     * <code>toString()</code> on the objects in question.
     */
    public static Comparator getStringRepresentationComparator() {
      return new Comparator() {
          public int compare(Object o1, Object o2) {
            return o1.toString().compareTo(o2.toString());
          }
        };
    }

    public static Comparator<boolean[]> getBooleanArrayComparator() {
      return (a1, a2) -> ArrayUtils.compareBooleanArrays(a1, a2);
    }

    public static <C extends Comparable> Comparator<C[]> getArrayComparator() {
      return (a1, a2) -> ArrayUtils.compareArrays(a1, a2);
    }

  }
