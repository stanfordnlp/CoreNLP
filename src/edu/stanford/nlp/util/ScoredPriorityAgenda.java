package edu.stanford.nlp.util;

import java.util.Comparator;

/**
 * Heap implementation of a priority agenda.
 *
 * @author Dan Klein
 */
public final class ScoredPriorityAgenda<T> implements Agenda<T> {
  private Heap<T> heap;

  public void verify() {
    ((ArrayHeap<T>) heap).verify();
  }

  public void add(T o) {
    heap.add(o);
  }

  public T next() {
    return heap.extractMin();
  }

  public boolean hasNext() {
    return !heap.isEmpty();
  }

  public int decreaseKey(T o) {
    return heap.decreaseKey(o);
  }

  @SuppressWarnings("unchecked")
  public ScoredPriorityAgenda() {
    Comparator cmp = ScoredComparator.DESCENDING_COMPARATOR;
    heap = new ArrayHeap<T>(cmp);
  }
}
