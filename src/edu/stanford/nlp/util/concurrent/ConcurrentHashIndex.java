package edu.stanford.nlp.util.concurrent;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;

/**
 * A fast threadsafe index that supports constant-time lookup in both directions. This
 * index is tuned for circumstances in which readers significantly outnumber writers.
 * 
 * @author Spence Green
 *
 * @param <E>
 */
public class ConcurrentHashIndex<E> extends AbstractCollection<E> implements Index<E>, RandomAccess {

  private static final long serialVersionUID = 6465313844985269109L;

  public static final int UNKNOWN_ID = -1;
  private static final int DEFAULT_INITIAL_CAPACITY = 100;

  private final Map<E,Integer> item2Index;
  private final List<E> index2Item;
  private final ReentrantReadWriteLock lock;

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
    this.item2Index = Generics.newHashMap(initialCapacity);
    this.index2Item = Generics.newArrayList(initialCapacity);
    lock = new ReentrantReadWriteLock();
  }

  @Override
  public E get(int i) {
    lock.readLock().lock();
    try {
      return index2Item.get(i);
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public int indexOf(E o) {
    lock.readLock().lock();
    try {
      Integer id = item2Index.get(o);
      return id == null ? UNKNOWN_ID : id;
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public int indexOf(E o, boolean add) {
    lock.readLock().lock();
    if ( ! item2Index.containsKey(o) && add) {
      lock.readLock().unlock();
      lock.writeLock().lock();
      try {
        // Recheck state because another thread might have already performed
        // the update
        if ( ! item2Index.containsKey(o)) {
          item2Index.put(o, index2Item.size());
          index2Item.add(o);
        }
        // Downgrade by acquiring read lock before releasing write lock
        lock.readLock().lock();
      } finally {
        lock.writeLock().unlock(); // Unlock write, still hold read
      }
    }
    
    try {
      Integer id = item2Index.get(o);
      return id == null ? UNKNOWN_ID : id;
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public boolean add(E o) {
    return indexOf(o, true) != UNKNOWN_ID;
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
    lock.readLock().lock();
    try {
      return Generics.newArrayList(item2Index.keySet());
    } finally {
      lock.readLock().unlock();
    }
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
  public void lock() {}

  @Override
  public void unlock() {}

  @Override
  public void saveToWriter(Writer out) throws IOException {
    final String nl = System.getProperty("line.separator");
    for (int i = 0, sz = size(); i < sz; i++) {
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
      for (int i = 0, size = size(); i < size; i++) {
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
    lock.readLock().lock();
    try {
      return index2Item.size();
    } finally {
      lock.readLock().unlock();
    }
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
    lock.writeLock().lock();
    try {
      item2Index.clear();
      index2Item.clear();
    } finally {
      lock.writeLock().unlock();
    }
  }
}
