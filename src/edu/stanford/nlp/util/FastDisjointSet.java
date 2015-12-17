package edu.stanford.nlp.util;

import java.util.*;

/**
 * Disjoint forest with path compression and union-by-rank.  The set
 * is unmodifiable except by unions.
 *
 * @author Dan Klein
 * @version 4/17/01
 */
public class FastDisjointSet<T> implements DisjointSet<T> {

  static class Element<TT> {
    Element<TT> parent;
    int rank;
    TT object;

    Element(TT o) {
      object = o;
      rank = 0;
      parent = this;
    }
  }

  private Map<T, Element<T>> objectToElement;

  private static <TTT> void linkElements(Element<TTT> e, Element<TTT> f) {
    if (e.rank > f.rank) {
      f.parent = e;
    } else {
      e.parent = f;
      if (e.rank == f.rank) {
        f.rank++;
      }
    }
  }

  private static <TTT> Element<TTT> findElement(Element<TTT> e) {
    if (e.parent == e) {
      return e;
    }
    Element<TTT> rep = findElement(e.parent);
    e.parent = rep;
    return rep;
  }

  @Override
  public T find(T o) {
    Element<T> e = objectToElement.get(o);
    if (e == null) {
      return null;
    }
    Element<T> element = findElement(e);
    return element.object;
  }

  @Override
  public void union(T a, T b) {
    Element<T> e = objectToElement.get(a);
    Element<T> f = objectToElement.get(b);
    if (e == null || f == null) {
      return;
    }
    if (e == f) {
      return;
    }
    linkElements(findElement(e), findElement(f));
  }

  public FastDisjointSet(Set<? extends T> objectSet) {
    objectToElement = Generics.newHashMap();
    for (T o : objectSet) {
      // create an element
      Element<T> e = new Element<>(o);
      objectToElement.put(o, e);
    }
  }

}
