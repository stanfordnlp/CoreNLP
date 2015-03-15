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

  class Element {
    Element parent;
    int rank;
    T object;

    Element(T o) {
      object = o;
      rank = 0;
      parent = this;
    }
  }

  Map<T, Element> objectToElement;

  private void linkElements(Element e, Element f) {
    if (e.rank > f.rank) {
      f.parent = e;
    } else {
      e.parent = f;
      if (e.rank == f.rank) {
        f.rank++;
      }
    }
  }

  private Element findElement(Element e) {
    if (e.parent == e) {
      return e;
    }
    Element rep = findElement(e.parent);
    e.parent = rep;
    return rep;
  }

  public T find(T o) {
    Element e = objectToElement.get(o);
    if (e == null) {
      return null;
    }
    Element element = findElement(e);
    return element.object;
  }

  public void union(T a, T b) {
    Element e = objectToElement.get(a);
    Element f = objectToElement.get(b);
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
    for (Iterator<? extends T> i = objectSet.iterator(); i.hasNext();) {
      // create an element
      T o = i.next();
      Element e = new Element(o);
      objectToElement.put(o, e);
    }
  }

 
}
