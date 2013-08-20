package edu.stanford.nlp.util;

import java.util.AbstractList;
import java.util.List;
import java.io.Serializable;

/**
 * List that returns elements in reverse order.
 */
public class ReversedList<E> extends AbstractList<E> implements Serializable {

  private final List<E> l;
  
  @Override
  public int size() {
    return l.size();
  }

  @Override
  public E get(int i) {
    return l.get(l.size()-1-i);
  }

  /** With this constructor, get() will return <code>null</code> for 
   *  elements outside the real list.
   */
  public ReversedList(List<E> l) {
    this.l = l;
  }

  private static final long serialVersionUID = 206477596643954354L;

}
