package edu.stanford.nlp.util.concurrent;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.RandomAccess;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;

/**
 * A fast threadsafe index that supports constant-time lookup in both directions. This
 * index is tuned for circumstances in which readers significantly outnumber writers.
 *
 * @author Spence Green
 *
 * @param <E> Element type to store
 */
public class ConcurrentHashIndex<E> extends AbstractCollection<E> implements Index<E>, RandomAccess {

  private static final long serialVersionUID = 6465313844985269109L;

  public static final int UNKNOWN_ID = -1;
  private static final int DEFAULT_INITIAL_CAPACITY = 100;

  private final ConcurrentHashMap<E,Integer> item2Index;
  private int indexSize;
  private final ReentrantLock lock;
  private final AtomicReference<Object[]> index2Item;

  /**
   * Constructor.
   */
  public ConcurrentHashIndex() {
    this(DEFAULT_INITIAL_CAPACITY);
  }

  /**
   * Constructor.
   *
   * @param initialCapacity
   */
  public ConcurrentHashIndex(int initialCapacity) {
    item2Index = new ConcurrentHashMap<>(initialCapacity);
    indexSize = 0;
    lock = new ReentrantLock();
    Object[] arr = new Object[initialCapacity];
    index2Item = new AtomicReference<>(arr);
  }

  @SuppressWarnings("unchecked")
  @Override
  public E get(int i) {
    Object[] arr = index2Item.get();
    if (i < indexSize) {
      // arr.length guaranteed to be == to size() given the
      // implementation of indexOf below.
      return (E) arr[i];
    }
    throw new ArrayIndexOutOfBoundsException(String.format("Out of bounds: %d >= %d", i, indexSize));
  }

  @Override
  public int indexOf(E o) {
    Integer id = item2Index.get(o);
    return id == null ? UNKNOWN_ID : id;
  }

  @Override
  public int addToIndex(E o) {
    Integer index = item2Index.get(o);
    if (index != null) {
      return index;
    }

    lock.lock();
    try {
      // Recheck state
      if (item2Index.containsKey(o)) {
        return item2Index.get(o);

      } else {
        final int newIndex = indexSize++;
        Object[] arr = index2Item.get();
        assert newIndex <= arr.length;
        if (newIndex == arr.length) {
          // Increase size of array if necessary
          Object[] newArr = new Object[2*newIndex];
          System.arraycopy(arr, 0, newArr, 0, arr.length);
          arr = newArr;
        }
        arr[newIndex] = o;
        index2Item.set(arr);
        item2Index.put(o, newIndex);
        return newIndex;
      }
    } finally {
      lock.unlock();
    }
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
  public boolean add(E o) {
    return addToIndex(o) != UNKNOWN_ID;
  }

  @Override
  public boolean addAll(Collection<? extends E> c) {
    boolean changed = false;
    for (E element: c) {
      changed |= add(element);
    }
    return changed;
  }

  @Override
  public List<E> objectsList() {
    return Generics.newArrayList(item2Index.keySet());
  }

  @Override
  public Collection<E> objects(final int[] indices) {
    return new AbstractList<E>() {
      @Override
      public E get(int index) {
        return ConcurrentHashIndex.this.get(indices[index]);
      }
      @Override
      public int size() {
        return indices.length;
      }
    };
  }

  @Override
  public boolean isLocked() {
    return false;
  }

  @Override
  public void lock() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void unlock() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void saveToWriter(Writer out) throws IOException {
    final String nl = System.getProperty("line.separator");
    for (int i = 0, sz = indexSize; i < sz; i++) {
      E o = get(i);
      if (o != null) {
        out.write(i + "=" + get(i) + nl);
      }
    }
  }

  @Override
  public void saveToFilename(String s) {
    PrintWriter bw = null;
    try {
      bw = IOUtils.getPrintWriter(s);
      for (int i = 0, size = indexSize; i < size; i++) {
        E o = get(i);
        if (o != null) {
          bw.printf("%d=%s%n", i, o.toString());
        }
      }
      bw.close();
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (bw != null) {
        bw.close();
      }
    }
  }

  @Override
  public Iterator<E> iterator() {
    return new Iterator<E>() {
      private int index = 0;
      private int size = ConcurrentHashIndex.this.size();
      @Override
      public boolean hasNext() {
        return index < size;
      }
      @Override
      public E next() {
        return ConcurrentHashIndex.this.get(index++);
      }
      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  @Override
  public int size() {
    return indexSize;
  }

  @Override
  public String toString() {
    StringBuilder buff = new StringBuilder("[");
    int i;
    final int size = size();
    for (i = 0; i < size; i++) {
      E e = get(i);
      if (e != null) {
        buff.append(i).append('=').append(e);
        if (i < (size-1)) buff.append(',');
      }
    }
    if (i < size()) buff.append("...");
    buff.append(']');
    return buff.toString();
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean contains(Object o) {
    return indexOf((E) o) != UNKNOWN_ID;
  }

  @Override
  public void clear() {
    lock.lock();
    try {
      item2Index.clear();
      indexSize = 0;
      Object[] arr = new Object[DEFAULT_INITIAL_CAPACITY];
      index2Item.set(arr);
    } finally {
      lock.unlock();
    }
  }
}
