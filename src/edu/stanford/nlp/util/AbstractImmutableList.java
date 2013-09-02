package edu.stanford.nlp.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * Default abstract implementation of an immutable list.  Implements
 * all List methods except get(int i) and size(), to be provided
 * by subclasses.  Operations that would mutate the list throw
 * UnsupportedOperationException.
 * 
 * @author dramage
 * @version May 25 2006
 */
public abstract class AbstractImmutableList<E> implements List<E> {
  //
  // Methods to be provided by subclass
  //
  
  public abstract E get(int index);
  
  public abstract int size();
  
  //
  // default implementations
  //
	
  public final boolean add(E o) {
    throw new UnsupportedOperationException();
  }

  public final void add(int index, E element) {
    throw new UnsupportedOperationException();
  }

  public final boolean addAll(Collection<? extends E> c) {
    throw new UnsupportedOperationException();
  }

  public final boolean addAll(int index, Collection<? extends E> c) {
    throw new UnsupportedOperationException();
  }

  public final void clear() {
    throw new UnsupportedOperationException();
  }

  public final boolean contains(Object o) {
    return indexOf(o) >= 0;
  }

  public final int indexOf(Object o) {
    int size = size();
    for (int i = 0; i < size; i++) {
      if (get(i).equals(o)) {
        return i;
      }
    }
    return -1;
  }

  public final boolean containsAll(Collection<?> c) {
    for (Object a : c) {
      if (!contains(a)) {
        return false;
      }
    }
    return true;
  }

  public final boolean isEmpty() {
    return size() == 0;
  }

  public final Iterator<E> iterator() {
    return new Iterator<E>() {
      private int position = 0;
      public boolean hasNext() {
        return position < size();
      }

      public E next() {
        return get(position++);
      }

      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  public final int lastIndexOf(Object o) {
    for (int i = size()-1; i >= 0; i--) {
      if (get(i).equals(o)) {
        return i;
      }
    }
    return -1;
  }

  public final ListIterator<E> listIterator() {
    throw new UnsupportedOperationException();
  }

  public final ListIterator<E> listIterator(int index) {
    throw new UnsupportedOperationException();
  }

  public final boolean remove(Object o) {
    throw new UnsupportedOperationException();
  }

  public final E remove(int index) {
    throw new UnsupportedOperationException();
  }

  public final boolean removeAll(Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  public final boolean retainAll(Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  public final E set(int index, E element) {
    throw new UnsupportedOperationException();
  }

  public final List<E> subList(final int fromIndex, final int toIndex) {
    return new AbstractImmutableList<E>() {

      // TODO: add in @Override when we compile with Java 1.6
      @Override
      public E get(int index) {
        return AbstractImmutableList.this.get(index+fromIndex);
      }

      // TODO: add in @Override when we compile with Java 1.6
      @Override
      public int size() {
        return toIndex-fromIndex;
      }
    };
  }

  public final Object[] toArray() {
    Object[] array = new Object[size()];
    int i = 0;
    for (Object obj : this) {
      array[i++] = obj;
    }
    return array;
  }

  @SuppressWarnings("unchecked")
  public final <T> T[] toArray(T[] a) {
    T[] array = (T[])java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), size());
    int i = 0;
    for (Object obj : this) {
      array[i++] = (T)obj;
    }
    return array;
  }
}
