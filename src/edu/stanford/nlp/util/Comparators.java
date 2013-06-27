package edu.stanford.nlp.util;

import java.util.*;


public class Comparators {

  // Copied from edu.stanford.nlp.natlog.util
  /**
   * Returns a new <code>Comparator</code> which is the result of chaining the
   * given <code>Comparator</code>s.  If the first <code>Comparator</code>
   * considers two objects unequal, its result is returned; otherwise, the
   * result of the second <code>Comparator</code> is returned.  Facilitates
   * sorting on primary and secondary keys.
   */
  public static <T> Comparator<T> chain(final Comparator<? super T> c1,
                                        final Comparator<? super T> c2) {
    return new Comparator<T>() {
      public int compare(T o1, T o2) {
        int x = c1.compare(o1, o2);
        return (x == 0 ? c2.compare(o1, o2) : x);
      }
    };
  }

  // Copied from edu.stanford.nlp.natlog.util
  /**
   * Returns a new <code>Comparator</code> which is the result of chaining the
   * given <code>Comparator</code>s.  Facilitates sorting on multiple keys.
   */
  public static <T> Comparator<T> chain(final List<Comparator<? super T>> c) {
    return new Comparator<T>() {
      public int compare(T o1, T o2) {
        int x = 0;
        Iterator<Comparator<? super T>> it = c.iterator();
        while (x == 0 && it.hasNext()) {
          x = it.next().compare(o1, o2);
        }
        return x;
      }
    };
  }

  public static <T> Comparator<T> chain(Comparator<? super T>... c) {
    return chain(Arrays.asList(c));
  }

  /**
   * Returns a new <code>Comparator</code> which is the reverse of the
   * given <code>Comparator</code>.
   */
  public static <T> Comparator<T> reverse(final Comparator<? super T> c) {
    return new Comparator<T>() {
      public int compare(T o1, T o2) {
        int x = c.compare(o1, o2);
        return -x;
      }
    };
  }

  /**
   * Returns a consistent ordering over two elements even if one of them is null
   * (as long as compareTo() is stable, of course).
   * 
   * Adapted from http://stackoverflow.com/a/481836
   */
  public static <T extends Comparable<? super T>> int nullSafeComparator(final T one, final T two) {
    if (one == null ^ two == null) {
      return (one == null) ? -1 : 1;
    }

    if (one == null && two == null) {
      return 0;
    }

    return one.compareTo(two);
  }
}
