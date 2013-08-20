package edu.stanford.nlp.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Stack implementation of the Agenda interface.
 *
 * @author Dan Klein
 */
public final class StackAgenda<T> implements Agenda<T> {
  private List<T> list;

  public StackAgenda() {
    list = new ArrayList<T>();
  }

  public void add(T o) {
    list.add(o);
  }

  public boolean hasNext() {
    return !list.isEmpty();
  }

  public T next() {
    int top = list.size() - 1;
    if (top >= 0) {
      return list.remove(top);
    }
    throw new java.util.NoSuchElementException();
  }

  public static void main(String[] args) {
    Agenda<String> a = new StackAgenda<String>();
    a.add("fred");
    a.add("william");
    a.add("jonas");
    while (a.hasNext()) {
      System.out.println(a.next());
    }
  }

}
