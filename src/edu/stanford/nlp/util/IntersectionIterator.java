package edu.stanford.nlp.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Iterator over the union of two sets.
 * <p/>
 * User: Dan Klein (klein@cs.stanford.edu)
 * Date: Oct 22, 2003
 * Time: 7:52:32 PM
 */
public class IntersectionIterator<T> extends FilteredIterator<T> {
  static class ContainmentFilter<T> implements Filter<T> {
    private static final long serialVersionUID = 2957370865554346129L;
    Set<T> s = null;

    public boolean accept(T o) {
      return s.contains(o);
    }

    ContainmentFilter(Set<T> s) {
      this.s = s;
    }
  }

  public IntersectionIterator(Set<T> x, Set<T> y) {
    super(x.iterator(), new ContainmentFilter<T>(y));
  }

  public static void main(String[] args) {
    Set<String> s1 = new HashSet<String>(Arrays.asList(new String[]{"a", "b", "c"}));
    Set<String> s2 = new HashSet<String>(Arrays.asList(new String[]{"a", "d", "b"}));
    Iterator<String> i = new IntersectionIterator<String>(s1, s2);
    while (i.hasNext()) {
      System.out.println("Accepted: " + i.next());
    }
  }
}
