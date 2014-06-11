package edu.stanford.nlp.util.concurrent;

import java.io.Serializable;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import edu.stanford.nlp.math.SloppyMath;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.Factory;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.logging.PrettyLogger;
import edu.stanford.nlp.util.logging.Redwood.RedwoodChannels;

/**
 * A threadsafe counter implemented as a lightweight wrapper around a 
 * ConcurrentHashMap.
 * 
 * @author Spence Green
 *
 * @param <E>
 */
public class ConcurrentHashCounter<E> implements Serializable, Counter<E>, Iterable<E> {

  private static final long serialVersionUID = -8077192206562696111L;

  private static final int DEFAULT_CAPACITY = 100;
  
  private final ConcurrentMap<E,AtomicDouble> map;
  private final AtomicDouble totalCount;
  private double defaultReturnValue = 0.0;
  
  public ConcurrentHashCounter() {
    this(DEFAULT_CAPACITY);
  }
  
  public ConcurrentHashCounter(int initialCapacity) {
    map = new ConcurrentHashMap<E,AtomicDouble>(initialCapacity);
    totalCount = new AtomicDouble();
  }

  @Override
  public Iterator<E> iterator() {
    return keySet().iterator();
  }

  @Override
  public Factory<Counter<E>> getFactory() {
    return new Factory<Counter<E>>() {
      private static final long serialVersionUID = 6076144467752914760L;
      @Override
      public Counter<E> create() {
        return new ConcurrentHashCounter<E>();
      }
    };
  }

  @Override
  public void setDefaultReturnValue(double value) {
    defaultReturnValue = value;
  }

  @Override
  public double defaultReturnValue() {
    return defaultReturnValue;
  }

  @Override
  public double getCount(Object key) {
    AtomicDouble v = map.get(key);
    return v == null ? defaultReturnValue : v.get();
  }

  @Override
  public void setCount(E key, double value) {
    AtomicDouble oldV = map.putIfAbsent(key, new AtomicDouble(value));
    if (oldV == null) {
      totalCount.addAndGet(value);
    } else {
      // This isn't right; see Guava.AtomicLongMap for an example of why
      for (;;) {
        // Do something
//        double v = map.pu
      }
//      double oldV = map.putIfAbsent(key, new AtomicDouble()).getAndSet(value);
//      totalCount.addAndGet(value - oldV);
    }
  }

  @Override
  public double incrementCount(E key, double value) {
    // Incorrect. See AtomicLongMap for an example of why
    double newV = map.putIfAbsent(key, new AtomicDouble()).addAndGet(value);
    totalCount.addAndGet(value);
    return newV;
  }

  @Override
  public double incrementCount(E key) {
    return incrementCount(key, 1.0);
  }

  @Override
  public double decrementCount(E key, double value) {
    return incrementCount(key, -value);
  }

  @Override
  public double decrementCount(E key) {
    return incrementCount(key, -1.0);
  }

  @Override
  public double logIncrementCount(E key, double value) {
    // TODO: This isn't right.
    double oldV;
    double newV;
    synchronized(map) {
      AtomicDouble v = map.putIfAbsent(key, new AtomicDouble());
      oldV = v.get();
      newV = SloppyMath.logAdd(value, oldV);
      map.get(key).set(newV);
    }
    totalCount.addAndGet(newV - oldV);
    return newV;
  }

  @Override
  public void addAll(Counter<E> counter) {
    Counters.addInPlace(this, counter);
  }

  @Override
  public double remove(E key) {
    // TODO: This isn't right.
    AtomicDouble oldV = map.remove(key);
    if (oldV != null) {
      double v = oldV.get();
      totalCount.addAndGet(-1.0 * v);
      return v;
    }
    return defaultReturnValue;
  }

  @Override
  public boolean containsKey(E key) {
    return map.containsKey(key);
  }

  @Override
  public Set<E> keySet() {
    return Collections.unmodifiableSet(map.keySet());
  }

  @Override
  public Collection<Double> values() {
    return new Collection<Double>() {
      @Override
      public int size() {
        return map.size();
      }
      @Override
      public boolean isEmpty() {
        return map.size() == 0;
      }
      @Override
      public boolean contains(Object o) {
        if (o instanceof Double) {
          return map.values().contains(new AtomicDouble((Double) o));
        }
        return false;
      }

      @Override
      public Iterator<Double> iterator() {
        // TODO Auto-generated method stub
        return null;
      }

      @Override
      public Object[] toArray() {
        return null;
      }

      @Override
      public <T> T[] toArray(T[] a) {
        return null;
      }

      @Override
      public boolean add(Double e) {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean remove(Object o) {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean containsAll(Collection<?> c) {
        // TODO Auto-generated method stub
        return false;
      }
      @Override
      public boolean addAll(Collection<? extends Double> c) {
        throw new UnsupportedOperationException();
      }
      @Override
      public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
      }
      @Override
      public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
      }
      @Override
      public void clear() {
        throw new UnsupportedOperationException();
      }
    };
  }

  @Override
  public Set<Entry<E, Double>> entrySet() {
    return new AbstractSet<Map.Entry<E,Double>>() {
      @Override
      public Iterator<Entry<E, Double>> iterator() {
        return new Iterator<Entry<E,Double>>() {
          final Iterator<Entry<E,AtomicDouble>> inner = map.entrySet().iterator();

          @Override
          public boolean hasNext() {
            return inner.hasNext();
          }

          @Override
          public Entry<E, Double> next() {
            return new Entry<E,Double>() {
              final Entry<E,AtomicDouble> e = inner.next();

              @Override
              public E getKey() {
                return e.getKey();
              }

              @Override
              public Double getValue() {
                return e.getValue().get();
              }

              @Override
              public Double setValue(Double value) {
                final double old = e.getValue().get();
                setCount(e.getKey(), value);
                e.getValue().set(value);
                return old;
              }
            };
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }

      @Override
      public int size() {
        return map.size();
      }
    };
  }

  @Override
  public void clear() {
    synchronized(map) {
      map.clear();
      totalCount.set(0);
    }
  }

  @Override
  public int size() {
    return map.size();
  }

  @Override
  public double totalCount() {
    return totalCount.get();
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if ( ! (o instanceof ConcurrentHashCounter)) {
      return false;
    } else {
      final ConcurrentHashCounter<E> other = (ConcurrentHashCounter<E>) o;
      return totalCount.get() == other.totalCount.get() && map.equals(other.map);
    }
  }

  /** Returns a hashCode which is the underlying Map's hashCode.
   *
   *  @return A hashCode.
   */
  @Override
  public int hashCode() {
    return map.hashCode();
  }

  /** Returns a String representation of the Counter, as formatted by
   *  the underlying Map.
   *
   *  @return A String representation of the Counter.
   */
  @Override
  public String toString() {
    return map.toString();
  }
  
  @Override
  public void prettyLog(RedwoodChannels channels, String description) {
    PrettyLogger.log(channels, description, map);
  }
}
