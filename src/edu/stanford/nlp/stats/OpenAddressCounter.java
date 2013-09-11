package edu.stanford.nlp.stats;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.doubles.DoubleCollection;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.io.Serializable;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Map.Entry;

import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.Factory;
import edu.stanford.nlp.util.logging.PrettyLogger;
import edu.stanford.nlp.util.logging.Redwood.RedwoodChannels;

/**
 * A counter backed by fastutil, an open address primitive collection map.
 *
 * Do not add methods to this file.  Convenience methods should be
 * added to {@link Counters}.
 *
 * @author dramage
 * @author cer
 */
public final class OpenAddressCounter<E> extends AbstractCounter<E> implements Serializable {

  private static final long serialVersionUID = 1L;

  /** Underlying open-address based map */
  private final AdjustableObject2DoubleOpenHashMap<E> map;

  /** Total value */
  private double total; // = 0.0;

  public OpenAddressCounter(float loadFactor) {
    map = new AdjustableObject2DoubleOpenHashMap<E>(Hash.DEFAULT_INITIAL_SIZE, loadFactor);
  }

  public OpenAddressCounter() {
    map = new AdjustableObject2DoubleOpenHashMap<E>();
  }

  public OpenAddressCounter(Counter<E> c) {
    this(c, Hash.DEFAULT_LOAD_FACTOR);
  }

  public OpenAddressCounter(Counter<E> c, float loadFactor) {
    map = new AdjustableObject2DoubleOpenHashMap<E>((int)(c.size()/loadFactor)+1, loadFactor);
    for (Entry<E, Double> e : c.entrySet()) {
      incrementCount(e.getKey(), e.getValue());
    }
  }

  /** {@inheritDoc} */
  public Factory<Counter<E>> getFactory() {
    return new Factory<Counter<E>>() {
      private static final long serialVersionUID = 5992407519116558008L;

      public Counter<E> create() {
        return new OpenAddressCounter<E>();
      }
    };
  }

  /** {@inheritDoc} */
  public void setDefaultReturnValue(double rv) {
    map.defaultReturnValue(rv);
  }

  /** {@inheritDoc} */
  public double defaultReturnValue() {
    return map.defaultReturnValue();
  }

  /** {@inheritDoc} */
  public double getCount(Object key) {
    return map.getDouble(key);
  }

  /** {@inheritDoc} */
  public double remove(E key) {
    double rv = map.removeDouble(key);
    total -= rv;
    return rv;
  }

  /** {@inheritDoc} */
  public void setCount(E key, double value) {
    total += value - map.put(key, value);
  }

  /** {@inheritDoc} */
  @Override
  public double incrementCount(E key, double amount) {
    total += amount;
    return map.adjust(key, amount) + amount;
  }

  /** {@inheritDoc} */
  public void clear() {
    map.clear();
    total = 0;
  }

  /** {@inheritDoc} */
  public boolean containsKey(E key) {
    return map.containsKey(key);
  }

  /** {@inheritDoc} */
  public int size() {
    return map.size();
  }

  /** {@inheritDoc} */
  public double totalCount() {
    return total;
  }

  /** {@inheritDoc} */
  public DoubleCollection values() {
    return map.values();
  }

  /** {@inheritDoc} */
  public Set<E> keySet() {
    return new AbstractSet<E>() {
      @Override
      public Iterator<E> iterator() {
        return map.keySet().iterator();
      }

      @Override
      public int size() {
        return map.size();
      }
    };
  }

  /** {@inheritDoc} */
  public Set<Entry<E, Double>> entrySet() {
    return ErasureUtils.<Set<Entry<E,Double>>>uncheckedCast(object2DoubleEntrySet());
  }

  /**
   * Returns a type-specific view of the entries in this set.  It is
   * safe to change their total values.
   *
   * @return A type-specific view of the entries in this set.
   */
  public ObjectSet<Object2DoubleMap.Entry<E>> object2DoubleEntrySet() {
    final ObjectSet<Object2DoubleMap.Entry<E>> set = map.object2DoubleEntrySet();

    return new AbstractObjectSet<Object2DoubleMap.Entry<E>>() {
      @Override
      public ObjectIterator<Object2DoubleMap.Entry<E>> iterator() {
        return new ObjectIterator<Object2DoubleMap.Entry<E>>() {
          final ObjectIterator<Object2DoubleMap.Entry<E>> iterator =
            set.iterator();

          double lastValue; // = 0.0;

          public int skip(int n) {
            return iterator.skip(n);
          }

          public boolean hasNext() {
            return iterator.hasNext();
          }

          public Object2DoubleMap.Entry<E> next() {
            final Object2DoubleMap.Entry<E> entry = iterator.next();
            lastValue = entry.getDoubleValue();
            return new CounterEntry(entry);
          }

          public void remove() {
            iterator.remove();
            total -= lastValue;
          }
        };
      }

      public boolean contains(Object o) {
        return set.contains(o);
      }

      public int size() {
        return set.size();
      }
    };
  }

  /** A wrapped entry view that allows setting */
  private class CounterEntry implements Object2DoubleMap.Entry<E> {
    private Object2DoubleMap.Entry<E> wrapped;

    public CounterEntry(Object2DoubleMap.Entry<E> wrapped) {
      this.wrapped = wrapped;
    }

    public double getDoubleValue() {
      return wrapped.getDoubleValue();
    }

    public double setValue(double value) {
      total += value - wrapped.getDoubleValue();
      return wrapped.setValue(value);
    }

    public E getKey() {
      return wrapped.getKey();
    }

    public Double getValue() {
      return wrapped.getValue();
    }

    public Double setValue(Double value) {
      return setValue(value.doubleValue());
    }
  }

  // NOTE: Using @inheritdoc to get back to Object's javadoc doesn't work
  // on a class that implements an interface in 1.6.  Weird, but there you go.

  /** Equality is defined over all Counter implementations.
   *  Two Counters are equal if they have the same keys explicitly stored
   *  with the same values.
   *  <p>
   *  Note that a Counter with a key with value defaultReturnValue will not
   *  be judged equal to a Counter that is lacking that key. In order for
   *  two Counters to be correctly judged equal in such cases, you should
   *  call Counters.retainNonDefaultValues() on both Counters first.
   *
   *  @param other Object to compare for equality
   *  @return Whether this is equal to other
   */
  @SuppressWarnings("unchecked")
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    } else if (!(other instanceof Counter)) {
      return false;
    } else if (!(other instanceof OpenAddressCounter)) {
      return Counters.equals(this, (Counter)other);
    } else {
      return this.map.equals(((OpenAddressCounter)other).map);
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

  /**
   * {@inheritDoc}
   */
  public void prettyLog(RedwoodChannels channels, String description) {
    PrettyLogger.log(channels, description, Counters.asMap(this));
  }
  
  /**
   * An OpenHashMap with ability to adjust values.
   *
   * @author dramage
   */
  private static class AdjustableObject2DoubleOpenHashMap<F> extends Object2DoubleOpenHashMap<F> {
    private static final long serialVersionUID = 1L;

    public AdjustableObject2DoubleOpenHashMap() {
      super();
    }

    public AdjustableObject2DoubleOpenHashMap(final int n, final float f) {
      super(n,f);
    }

    /**
     * If key k is present in the map, adjusts its value by adding adjust;
     * if not present sets the value to be adjust.
     * @param k A key
     * @param adjust The amount to change its value by
     * @return The value in the map before the adjustment/put.
     */
    public double adjust(final F k, final double adjust) {
      final int i = findInsertionPoint( k );
      if (i < 0) {
        final double oldvalue = value[-i-1];
        value[-i-1] += adjust;
        return oldvalue;
      }
      if ( state[i] == FREE ) free--;
      state[i] = OCCUPIED;
      key[i] = k;
      value[i] = adjust;
      if ( ++count >= maxFill ) {
        int newP = Math.min( p + growthFactor, PRIMES.length - 1 );
        // Just to be sure that size changes when p is very small.
        while( PRIMES[ newP ] == PRIMES[ p ] ) newP++;
        rehash( newP ); // Table too filled, let's rehash
      }
      if ( free == 0 ) rehash( p );
      return defRetValue;
    }

  } // end static class AdjustableObject2DoubleOpenHashMap
  
}
