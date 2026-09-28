package edu.stanford.nlp.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;


/**
 * Utilities for sets.
 *
 * @author Roger Levy, Bill MacCartney
 */
public class Sets {

  // private to prevent instantiation
  private Sets() {}

  /**
   * Returns a new HashSet of the results of applying a function to each element of a set.
   *
   * @param <E> the type of the elements of {@code oldSet}
   * @param <F> the type of the results of {@code lambda}
   * @param oldSet the set to map over
   * @param lambda the function to apply to each element
   * @return a new set of the function's results
   */
  public static <E, F> Set<F> map(Set<E> oldSet, Function<E, F> lambda) {
    Set<F> newSet = new HashSet<>();
    for (E e : oldSet) {
      newSet.add(lambda.apply(e));
    }
    return newSet;
  }

  /**
   * Returns the set cross product of s1 and s2, as <code>Pair</code>s
   *
   * @param <E> the type of the elements of {@code s1}
   * @param <F> the type of the elements of {@code s2}
   * @param s1 the set of first elements
   * @param s2 the set of second elements
   * @return a new set of all pairs of an element of {@code s1} and an element of {@code s2}
   */
  public static <E,F> Set<Pair<E,F>> cross(Set<E> s1, Set<F> s2) {
    Set<Pair<E,F>> s = Generics.newHashSet();
    for (E o1 : s1) {
      for (F o2 : s2) {
        s.add(new Pair<>(o1, o2));
      }
    }
    return s;
  }

  /**
   * Returns the difference of sets s1 and s2.
   *
   * @param <E> the type of the elements
   * @param s1 the first set
   * @param s2 the second set
   * @return a new set of the elements of {@code s1} which are not in {@code s2}
   */
  public static <E> Set<E> diff(Set<E> s1, Set<E> s2) {
    Set<E> s = Generics.newHashSet();
    for (E o : s1) {
      if (!s2.contains(o)) {
        s.add(o);
      }
    }
    return s;
  }

  /**
   * Returns the symmetric difference of sets s1 and s2 (i.e. all elements that are in only one of the two sets)
   *
   * @param <E> the type of the elements
   * @param s1 the first set
   * @param s2 the second set
   * @return a new set of the symmetric difference
   */
  public static <E> Set<E> symmetricDiff(Set<E> s1, Set<E> s2) {
    Set<E> s = Generics.newHashSet();
    for (E o : s1) {
      if (!s2.contains(o)) {
        s.add(o);
      }
    }
    for (E o : s2) {
      if (!s1.contains(o)) {
        s.add(o);
      }
    }
    return s;
  }

  /**
   * Returns the union of sets s1 and s2.
   *
   * @param <E> the type of the elements
   * @param s1 the first set
   * @param s2 the second set
   * @return a new set of the union
   */
  public static <E> Set<E> union(Set<E> s1, Set<E> s2) {
    Set<E> s = Generics.newHashSet();
    s.addAll(s1);
    s.addAll(s2);
    return s;
  }

  /**
   * Returns the intersection of sets s1 and s2.
   *
   * @param <E> the type of the elements
   * @param s1 the first set
   * @param s2 the second set
   * @return a new set of the intersection
   */
  public static <E> Set<E> intersection(Set<E> s1, Set<E> s2) {
    Set<E> s = Generics.newHashSet();
    s.addAll(s1);
    s.retainAll(s2);
    return s;
  }

  /**
   * Returns true if there is at least element that is in both s1 and s2. Faster
   * than calling intersection(Set,Set) if you don't need the contents of the
   * intersection.
   *
   * @param <E> the type of the elements
   * @param s1 the first set
   * @param s2 the second set
   * @return true if the sets share at least one element
   */
  public static <E> boolean intersects(Set<E> s1, Set<E> s2) {
    // *ahem* It would seem that Java already had this method. Hopefully this
    // stub will help people find it better than I did.
    return !Collections.disjoint(s1, s2);
  }

  /**
   * Returns the powerset (the set of all subsets) of set s.
   * The elements of {@code s} are temporarily removed and re-added while
   * this runs, so {@code s} must be modifiable.
   *
   * @param <E> the type of the elements
   * @param s the set whose subsets to generate
   * @return a new set of new sets, one for each subset of {@code s}
   */
  public static <E> Set<Set<E>> powerSet(Set<E> s) {
    if (s.isEmpty()) {
      Set<Set<E>> h = Generics.newHashSet();
      Set<E> h0 = Generics.newHashSet(0);
      h.add(h0);
      return h;
    } else {
      Iterator<E> i = s.iterator();
      E elt = i.next();
      s.remove(elt);
      Set<Set<E>> pow = powerSet(s);
      Set<Set<E>> pow1 = powerSet(s);
      // for (Iterator j = pow1.iterator(); j.hasNext();) {
      for (Set<E> t : pow1) {
        // Set<E> t = Generics.newHashSet((Set<E>) j.next());
        t.add(elt);
        pow.add(t);
      }
      s.add(elt);
      return pow;
    }
  }

  /**
   * Tests whether two sets are equal.  If not, throws an assertion
   * and gives a detailed report on the differences.  May be long
   * depending on the sizes of the sets!
   *
   * @param first a set to compare
   * @param second a set to compare against
   * @param firstName the name of the first set, used if an error occurs
   * @param secondName the name of the second set, used if an error occurs
   * @param outputShared output the common values for the two sets
   * @param errorMessage a Supplier of an error message, in case it is expensive to generate
   * @param <E> the type of the elements
   * @throws AssertionError if the two sets are not equal
   */
  public static <E> void assertEquals(Set<E> first, Set<E> second,
                                      String firstName, String secondName,
                                      boolean outputShared, Supplier<String> errorMessage) {
    if (first.equals(second)) {
      return;
    }

    // now we know something is different.  find out what and throw an assertion
    Set<E> firstExtras = diff(first, second);
    Set<E> secondExtras = diff(second, first);
    StringBuilder builder = new StringBuilder();
    builder.append(errorMessage.get());
    builder.append("\n");
    if (firstExtras.size() > 0) {
      builder.append("-- Extra results in " + firstName + ": --\n");
      for (E extra : firstExtras) {
        builder.append(extra == null ? "(null)" : extra.toString());
        builder.append("\n");
      }
    }
    if (secondExtras.size() > 0) {
      builder.append("-- Extra results in " + secondName + ": --\n");
      for (E extra : secondExtras) {
        builder.append(extra == null ? "(null)" : extra.toString());
        builder.append("\n");
      }
    }

    if (outputShared) {
      Set<E> shared = intersection(first, second);
      if (shared.size() > 0) {
        builder.append("-- Common results in " + firstName + " and " + secondName + ": --\n");
        for (E extra : shared) {
          builder.append(extra == null ? "(null)" : extra.toString());
          builder.append("\n");
        }
      }
    }

    throw new AssertionError(builder.toString());
  }

  /**
   * Prints the powerset of the set {a, b, c}, as a quick demonstration.
   *
   * @param args ignored
   */
  public static void main(String[] args) {
    Set<String> h = Generics.newHashSet();
    h.add("a");
    h.add("b");
    h.add("c");
    Set<Set<String>> pow = powerSet(h);
    System.out.println(pow);
  }

}
