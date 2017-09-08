package old.edu.stanford.nlp.util;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;


/**
 * Utilities for sets.
 *
 * @author Roger Levy, Bill MacCartney
 */
public class Sets {

  // private to prevent instantiation
  private Sets() {}


  /**
   * Returns the set cross product of s1 and s2, as <code>Pair</code>s
   */
  public static <E,F> Set<Pair<E,F>> cross(Set<E> s1, Set<F> s2) {
    Set<Pair<E,F>> s = new HashSet<Pair<E,F>>();
    for (E o1 : s1) {
      for (F o2 : s2) {
        s.add(new Pair<E,F>(o1, o2));
      }
    }
    return s;
  }

  /**
   * Returns the difference of sets s1 and s2.
   */
  public static <E> Set<E> diff(Set<E> s1, Set<E> s2) {
    Set<E> s = new HashSet<E>();
    for (E o : s1) {
      if (!s2.contains(o)) {
        s.add(o);
      }
    }
    return s;
  }

  /**
   * Returns the union of sets s1 and s2.
   */
  public static <E> Set<E> union(Set<E> s1, Set<E> s2) {
    Set<E> s = new HashSet<E>();
    s.addAll(s1);
    s.addAll(s2);
    return s;
  }

  /**
   * Returns the intersection of sets s1 and s2.
   */
  public static <E> Set<E> intersection(Set<E> s1, Set<E> s2) {
    Set<E> s = new HashSet<E>();
    s.addAll(s1);
    s.retainAll(s2);
    return s;
  }

  /**
   * Returns the powerset (the set of all subsets) of set s.
   */
  public static <E> Set<Set<E>> powerSet(Set<E> s) {
    if (s.isEmpty()) {
      Set<Set<E>> h = new HashSet<Set<E>>();
      Set<E> h0 = new HashSet<E>(0);
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
        // Set<E> t = new HashSet<E>((Set<E>) j.next());
        t.add(elt);
        pow.add(t);
      }
      s.add(elt);
      return pow;
    }
  }

  public static void main(String[] args) {
    Set<String> h = new HashSet<String>();
    h.add("a");
    h.add("b");
    h.add("c");
    Set<Set<String>> pow = powerSet(h);
    System.out.println(pow);
  }

}
