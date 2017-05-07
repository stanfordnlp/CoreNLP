package edu.stanford.nlp.util;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * A hash set supporting full concurrency of retrievals and
 * high expected concurrency for updates but with an (adjustable) maximum size.
 * The maximum only prevents further add operations. It doesn't stop the maximum
 * being exceeded when first loaded or via an addAll(). This is deliberate!
 *
 * @author Christopher Manning
 * @param <E> the type of elements maintained by this set
 */
public class MaxSizeConcurrentHashSet<E> implements Set<E>, Serializable {

  private final ConcurrentMap<E, Boolean> m;
  private transient Set<E> s; // the keySet of the Map
  private int maxSize;

  /** Create a ConcurrentHashSet with no maximum size. */
  public MaxSizeConcurrentHashSet() {
    this(-1);
  }

  /** Create a ConcurrentHashSet with the maximum size given. */
  public MaxSizeConcurrentHashSet(int maxSize) {
    this.m = new ConcurrentHashMap<>();
    this.maxSize = maxSize;
    init();
  }

  /** Create a ConcurrentHashSet with the elements in s.
   *  This set has no maximum size.
   */
  public MaxSizeConcurrentHashSet(Set<? extends E> s) {
    this.m = new ConcurrentHashMap<>(Math.max(s.size(), 16));
    init();
    addAll(s);
    this.maxSize = -1;
  }

  private void init() {
    this.s = m.keySet();
  }

  public int getMaxSize() {
    return maxSize;
  }

  public void setMaxSize(int maxSize) {
    this.maxSize = maxSize;
  }

  /** Adds the element if the set is not already full. Otherwise, silently
   *  doesn't add it.
   *
   *  @param e The element
   *  @return true iff the element was added. This is slightly different from the semantics
   *      of a normal Set which returns true if the item didn't used to be there and was added.
   *      Here it only returns true if it was added.
   */
  @Override
  public boolean add(E e) {
    synchronized(this) {
      if (maxSize >= 0 && size() >= maxSize) {
        // can't put new value
        return false;
      } else {
        return m.put(e, Boolean.TRUE) == null;
      }
    }
  }

  @Override public void clear()               {        m.clear(); }
  @Override public int size()                 { return m.size(); }
  @Override public boolean isEmpty()          { return m.isEmpty(); }
  @Override public boolean contains(Object o) { return m.containsKey(o); }
  @Override public boolean remove(Object o)   { return m.remove(o) != null; }
  @Override public Iterator<E> iterator()     { return s.iterator(); }
  @Override public Object[] toArray()         { return s.toArray(); }
  @Override public <T> T[] toArray(T[] a)     { return s.toArray(a); }
  @Override public String toString()          { return s.toString(); }
  @Override public int hashCode()             { return s.hashCode(); }
  @Override public boolean equals(Object o)   { return s.equals(o); }
  @Override public boolean containsAll(Collection<?> c) {return s.containsAll(c);}
  @Override public boolean removeAll(Collection<?> c)   {return s.removeAll(c);}
  @Override public boolean retainAll(Collection<?> c)   {return s.retainAll(c);}

  /** Add all the items.
   *  This doesn't use the add method, because we want to bypass the limit here.
   */
  @Override
  public boolean addAll(Collection<? extends E> c) {
    boolean added = false;
    for (E item : c) {
      if (m.put(item, Boolean.TRUE) == null) {
        added = true;
      }
    }
    return added;
  }

  // Override default methods in Collection
  @Override public void forEach(Consumer<? super E> action) { s.forEach(action);}
  @Override public boolean removeIf(Predicate<? super E> filter) { return s.removeIf(filter);}
  @Override public Spliterator<E> spliterator()     {return s.spliterator();}
  @Override public Stream<E> stream()               {return s.stream();}
  @Override public Stream<E> parallelStream()       {return s.parallelStream();}

  private static final long serialVersionUID = 1L;

  private void readObject(java.io.ObjectInputStream stream)
          throws IOException, ClassNotFoundException {
    stream.defaultReadObject();
    init();
  }

}
