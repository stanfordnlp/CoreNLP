package edu.stanford.nlp.util;

import java.util.*;

/**
 * Concatenates an iterator over iterators into one long iterator.
 *
 * @author Dan Klein, Bill MacCartney
 */
public class SuperIterator<E> extends AbstractIterator<E> {

  private Iterator<Iterator<E>> sourceIterators;
  private Iterator<E> currentIterator;
  private Iterator<E> lastIteratorToReturn;

  @Override
  public boolean hasNext() {
    if (currentIterator.hasNext())
      return true;
    return false;
  }

  @Override
  public E next() {
    if (currentIterator.hasNext()) {
      E e = currentIterator.next();
      lastIteratorToReturn = currentIterator;
      advance();
      return e;
    }
    throw new NoSuchElementException();
  }

  private void advance() {
    while (!currentIterator.hasNext() && sourceIterators.hasNext()) {
      currentIterator = sourceIterators.next();
    }
  }

  @Override
  public void remove() {
    if (lastIteratorToReturn == null)
      throw new IllegalStateException();
    currentIterator.remove();
  }

  public SuperIterator(Iterator<Iterator<E>> sourceIterators) {
    this.sourceIterators = sourceIterators;
    this.currentIterator = (new ArrayList<E>()).iterator();
    this.lastIteratorToReturn = null;
    advance();
  }

  public SuperIterator(Collection<Iterator<E>> iteratorCollection) {
    this(iteratorCollection.iterator());
  }

  public static void main(String[] args) {
    List<String> list0 = Collections.emptyList();
    List<String> list1 = Arrays.asList("a b c d".split(" "));
    List<String> list2 = Arrays.asList("e f".split(" "));
    List<Iterator<String>> iterators = new ArrayList<Iterator<String>>();
    iterators.add(list1.iterator());
    iterators.add(list0.iterator());
    iterators.add(list2.iterator());
    iterators.add(list0.iterator());
    Iterator<String> iterator = new SuperIterator<String>(iterators);
    while (iterator.hasNext()) {
      System.out.println(iterator.next());
    }
  }

}
