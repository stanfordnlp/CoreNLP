package edu.stanford.nlp.util;

import java.io.Writer;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * A class that has a backing index, such as a hash index you don't
 * want changed, and another index which will hold extra entries that
 * get added during the life of the index.
 * <br>
 * It is important that nothing else changes the backing index while
 * a DeltaIndex is in use.  The behavior of this index is
 * undefined if the backing index changes, although in general the new
 * entries in the backing index will be ignored.
 *
 * @author John Bauer
 */
public class DeltaIndex<E> extends AbstractCollection<E> implements Index<E> {

  private static final long serialVersionUID = -1459230891686013411L;

  private final Index<E> backingIndex;
  private final Index<E> spilloverIndex;

  private final int backingIndexSize;

  private boolean locked;


  public DeltaIndex(Index<E> backingIndex) {
    this(backingIndex, new HashIndex<>());
  }

  public DeltaIndex(Index<E> backingIndex, Index<E> spilloverIndex) {
    this.backingIndex = backingIndex;
    this.spilloverIndex = spilloverIndex;

    backingIndexSize = backingIndex.size();
  }

  @Override
  public int size() {
    return backingIndex.size() + spilloverIndex.size();
  }

  @Override
  public E get(int i) {
    if (i < backingIndexSize) {
      return backingIndex.get(i);
    } else {
      return spilloverIndex.get(i - backingIndexSize);
    }
  }

  @Override
  public int indexOf(E o) {
    int index = backingIndex.indexOf(o);
    if (index >= 0) {
      return index;
    }

    index = spilloverIndex.indexOf(o);
    if (index >= 0) {
      return index + backingIndexSize;
    }
    return index; // i.e., return -1
  }

  @Override
  public int addToIndex(E o) {
    int index = backingIndex.indexOf(o);
    if (index >= 0) {
      return index;
    }

    if (locked) {
      index = spilloverIndex.indexOf(o);
    } else {
      index = spilloverIndex.addToIndex(o);
    }
    if (index >= 0) {
      return index + backingIndexSize;
    }
    return index; // i.e., return -1
  }

  @Override
  @Deprecated
  public int indexOf(E o, boolean add) {
    if (add) {
      return addToIndex(o);
    } else {
      return indexOf(o);
    }
  }

  @Override
  public List<E> objectsList() {
    List<E> result = new ArrayList<>();
    if (result.size() > backingIndexSize) {
      // we told you not to do this
      result.addAll(backingIndex.objectsList().subList(0, backingIndexSize));
    } else {
      result.addAll(backingIndex.objectsList());
    }

    result.addAll(spilloverIndex.objectsList());
    return Collections.unmodifiableList(result);
  }

  @Override
  public Collection<E> objects(int[] indices) {
    List<E> result = new ArrayList<>();
    for (int index : indices) {
      result.add(get(index));
    }
    return result;
  }

  @Override
  public boolean isLocked() {
    return locked;
  }

  @Override
  public void lock() {
    locked = true;
  }

  @Override
  public void unlock() {
    locked = false;
  }


  @Override
  public void saveToWriter(Writer out) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void saveToFilename(String s) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean contains(Object o) {
    return backingIndex.contains(o) || spilloverIndex.contains(o);
  }

  @Override
  public boolean add(E e) {
    if (backingIndex.contains(e))
      return false;
    return spilloverIndex.add(e);
  }

  @Override
  public boolean addAll(Collection<? extends E> c) {
    boolean changed = false;
    for (E e : c) {
      if (add(e)) {
        changed = true;
      }
    }
    return changed;
  }


  /**
   * We don't want to change the backing index in any way, and "clear"
   * would have to entail doing that, so we just throw an
   * UnsupportedOperationException instead
   */
  @Override
  public void clear() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isEmpty() {
    return backingIndexSize == 0 && spilloverIndex.size() == 0;
  }

  /**
   * This is one instance where elements added to the backing index
   * will show up in this index's operations
   */
  @Override
  public Iterator<E> iterator() {
    return new Iterator<E>() {
      Iterator<E> backingIterator = backingIndex.iterator();
      Iterator<E> spilloverIterator = spilloverIndex.iterator();

      @Override
      public boolean hasNext() {
        return backingIterator.hasNext() || spilloverIterator.hasNext();
      }

      @Override
      public E next() {
        if (backingIterator.hasNext()) {
          return backingIterator.next();
        } else {
          return spilloverIterator.next();
        }
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  /**
   * super ghetto
   */
  public String toString() {
    return backingIndex.toString() + "," + spilloverIndex.toString();
  }
}