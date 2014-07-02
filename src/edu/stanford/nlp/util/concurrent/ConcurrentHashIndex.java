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
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;

/**
 * A fast threadsafe index.
 * 
 * @author Spence Green
 *
 * @param <E>
 */
public class ConcurrentHashIndex<E> extends AbstractCollection<E> implements Index<E>, RandomAccess {

  private static final long serialVersionUID = 6465313844985269109L;

  private static final int UNKNOWN_ID = -1;

  private ConcurrentHashMap<E,Integer> item2Index = new ConcurrentHashMap<E,Integer>();
  private ConcurrentHashMap<Integer,E> index2Item = new ConcurrentHashMap<Integer,E>();
  private AtomicInteger index = new AtomicInteger();

  @Override
  public E get(int i) {
    return index2Item.get(i);
  }

  @Override
  public int indexOf(E o) {
    Integer id = item2Index.get(o);
    return id == null ? UNKNOWN_ID : id;
  }

  @Override
  public int indexOf(E o, boolean add) {
    Integer atomic = item2Index.get(o);
    if (atomic == null) {
      if (add) {
        final int newIndex = index.getAndIncrement();
        atomic = item2Index.putIfAbsent(o, newIndex);
        if (atomic == null) {
          index2Item.put(newIndex, o);
          return newIndex;
        } else {
          return item2Index.get(o);
        }
      } else {
        return UNKNOWN_ID;
      }
    } else {
      return atomic;
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
    return Generics.newArrayList(item2Index.keySet());
  }

  @Override
  public Collection<E> objects(final int[] indices) {
    return new AbstractList<E>() {
      @Override
      public E get(int index) {
        return get(indices[index]);
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
      int size = size();
      for (int i = 0; i < size; i++) {
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
      private final List<Integer> sortedKeys = Generics.newArrayList(new TreeSet<Integer>(index2Item.keySet()));
      private int index = 0;
      @Override
      public boolean hasNext() {
        return index < sortedKeys.size();
      }
      @Override
      public E next() {
        return index2Item.get(index++);
      }
      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  @Override
  public int size() {
    return index.get();
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
    synchronized(this) {
      item2Index = new ConcurrentHashMap<E,Integer>();
      index2Item = new ConcurrentHashMap<Integer,E>();
      index = new AtomicInteger();
    }
  }
}
