package edu.stanford.nlp.stats;

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
import edu.stanford.nlp.util.Factory;
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
public class ThreadsafeCounter<E> implements Serializable, Counter<E>, Iterable<E> {

  private static final long serialVersionUID = -8077192206562696111L;

  private static final int DEFAULT_CAPACITY = 100;
  
  private final ConcurrentMap<E,Double> map;
  private double totalCount = 0.0;
  private double defaultReturnValue = 0.0;
  
  public ThreadsafeCounter() {
    this(DEFAULT_CAPACITY);
  }
  
  public ThreadsafeCounter(int initialCapacity) {
    map = new ConcurrentHashMap<E,Double>(initialCapacity);
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
        return new ThreadsafeCounter<E>();
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
    Double v = map.get(key);
    return v == null ? defaultReturnValue : (double) v;
  }

  @Override
  public void setCount(E key, double value) {
    Double oldV = map.put(key, value);
    totalCount -= (oldV == null ? 0.0 : (double) oldV);
    totalCount += value;
  }

  @Override
  public double incrementCount(E key, double value) {
    double newV;
    synchronized(map) {
      Double v = map.get(key);
      newV = value + (v == null ? 0.0 : (double) v);
      map.put(key, newV);
    }
    totalCount += value;
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
    double oldV;
    double newV;
    synchronized(map) {
      Double v = map.get(key);
      oldV = v == null ? 0.0 : (double) v;
      newV = SloppyMath.logAdd(value, oldV);
      map.put(key, newV);
    }
    totalCount += newV - oldV;
    return newV;
  }

  @Override
  public void addAll(Counter<E> counter) {
    Counters.addInPlace(this, counter);
  }

  @Override
  public double remove(E key) {
    Double oldV;
    synchronized(map) {
      oldV = map.remove(key);
      totalCount -= oldV == null ? 0.0 : (double) oldV;
    }
    return oldV == null ? defaultReturnValue : (double) oldV;
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
    return Collections.unmodifiableCollection(map.values());
  }

  @Override
  public Set<Entry<E, Double>> entrySet() {
    return new AbstractSet<Map.Entry<E,Double>>() {
      @Override
      public Iterator<Entry<E, Double>> iterator() {
        return new Iterator<Entry<E,Double>>() {
          final Iterator<Entry<E,Double>> inner = map.entrySet().iterator();

          @Override
          public boolean hasNext() {
            return inner.hasNext();
          }

          @Override
          public Entry<E, Double> next() {
            return new Entry<E,Double>() {
              final Entry<E,Double> e = inner.next();

              @Override
              public E getKey() {
                return e.getKey();
              }

              @Override
              public Double getValue() {
                return e.getValue();
              }

              @Override
              public Double setValue(Double value) {
                final double old = e.getValue();
                setCount(e.getKey(), value);
                e.setValue(value);
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
      totalCount = 0.0;
    }
  }

  @Override
  public int size() {
    return map.keySet().size();
  }

  @Override
  public double totalCount() {
    return totalCount;
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if ( ! (o instanceof ThreadsafeCounter)) {
      return false;
    } else {
      final ThreadsafeCounter<E> other = (ThreadsafeCounter<E>) o;
      // Careful! Don't want a race condition between the first term of
      // the conjunction and the second.
      synchronized(map) {
        synchronized(other.map) {
          return totalCount == other.totalCount && map.equals(other.map);
        }
      }
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
