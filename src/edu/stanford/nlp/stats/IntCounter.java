package edu.stanford.nlp.stats;

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;

import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.PrettyLogger;
import edu.stanford.nlp.util.logging.Redwood.RedwoodChannels;

/**
 * A specialized kind of hash table (or map) for storing numeric counts for
 * objects. It works like a Map,
 * but with different methods for easily getting/setting/incrementing counts
 * for objects and computing various functions with the counts.
 * The Counter constructor
 * and {@code addAll} method can be used to copy another Counter's contents
 * over. This class also provides access
 * to Comparators that can be used to sort the keys or entries of this Counter
 * by the counts, in either ascending or descending order.
 *
 * @author Dan Klein (klein@cs.stanford.edu)
 * @author Joseph Smarr (jsmarr@stanford.edu)
 * @author Teg Grenager (grenager@stanford.edu)
 * @author Galen Andrew
 * @author Christopher Manning
 */
public class IntCounter<E> extends AbstractCounter<E> implements Serializable {

  @SuppressWarnings({"NonSerializableFieldInSerializableClass"})
  private Map<E, MutableInteger>  map;
  @SuppressWarnings("unchecked")
  private MapFactory mapFactory;
  private int totalCount;
  private int defaultValue; // = 0;

  /**
   * Default comparator for breaking ties in argmin and argmax.
   */
  private static final Comparator<Object> naturalComparator = new NaturalComparator<>();
  private static final long serialVersionUID = 4;

  // CONSTRUCTORS

  /**
   * Constructs a new (empty) Counter.
   */
  public IntCounter() {
    this(MapFactory.<E,MutableInteger>hashMapFactory());
  }

  /**
   * Pass in a MapFactory and the map it vends will back your counter.
   */
  public IntCounter(MapFactory<E,MutableInteger> mapFactory) {
    this.mapFactory = mapFactory;
    map = mapFactory.newMap();
    totalCount = 0;
  }

  /**
   * Constructs a new Counter with the contents of the given Counter.
   */
  public IntCounter(IntCounter<E> c) {
    this(c.mapFactory);
    addAll(c);
  }


  // STANDARD ACCESS MODIFICATION METHODS
  public MapFactory<E, MutableInteger> getMapFactory() {
    return ErasureUtils.<MapFactory<E,MutableInteger>>uncheckedCast(mapFactory);
  }

  public void setDefaultReturnValue(double rv) {
    defaultValue = (int) rv;
  }

  public void setDefaultReturnValue(int rv) {
    defaultValue = rv;
  }

  public double defaultReturnValue() {
    return defaultValue;
  }


  /**
   * Returns the current total count for all objects in this Counter.
   * All counts are summed each time, so cache it if you need it repeatedly.
   * @return The current total count for all objects in this Counter.
   */
  public int totalIntCount() {
    return totalCount;
  }

  public double totalDoubleCount() {
    return totalCount;
  }

  /**
   * Returns the total count for all objects in this Counter that pass the
   * given Filter. Passing in a filter that always returns true is equivalent
   * to calling {@link #totalCount()}.
   */
  public int totalIntCount(Predicate<E> filter) {
    int total = 0;
    for (E key : map.keySet()) {
      if (filter.test(key)) {
        total += getIntCount(key);
      }
    }
    return (total);
  }

  public double totalDoubleCount(Predicate<E> filter) {
    return totalIntCount(filter);
  }

  public double totalCount(Predicate<E> filter) {
    return totalDoubleCount(filter);
  }

  /**
   * Returns the mean of all the counts (totalCount/size).
   */
  public double averageCount() {
    return totalCount() / map.size();
  }

  /**
   * Returns the current count for the given key, which is 0 if it hasn't
   * been
   * seen before. This is a convenient version of {@code get} that casts
   * and extracts the primitive value.
   */
  public double getCount(Object key) {
    return getIntCount(key);
  }

  public String getCountAsString(E key) {
    return Integer.toString(getIntCount(key));
  }

  /**
   * Returns the current count for the given key, which is 0 if it hasn't
   * been
   * seen before. This is a convenient version of {@code get} that casts
   * and extracts the primitive value.
   */
  public int getIntCount(Object key) {
    MutableInteger count =  map.get(key);
    if (count == null) {
      return defaultValue; // haven't seen this object before -> 0 count
    }
    return count.intValue();
  }

  /**
   * This has been de-deprecated in order to reduce compilation warnings, but
   * really you should create a {@link edu.stanford.nlp.stats.Distribution} instead of using this method.
   */
  public double getNormalizedCount(E key) {
    return getCount(key) / (totalCount());
  }

  /**
   * Sets the current count for the given key. This will wipe out any existing
   * count for that key.
   * <p>
   * To add to a count instead of replacing it, use
   * {@link #incrementCount(Object,int)}.
   */
  public void setCount(E key, int count) {
    if (tempMInteger == null) {
      tempMInteger = new MutableInteger();
    }
    tempMInteger.set(count);
    tempMInteger = map.put(key, tempMInteger);


    totalCount += count;
    if (tempMInteger != null) {
      totalCount -= tempMInteger.intValue();
    }

  }

  public void setCount(E key, String s) {
    setCount(key, Integer.parseInt(s));
  }

  // for more efficient memory usage
  private transient MutableInteger tempMInteger = null;

  /**
   * Sets the current count for each of the given keys. This will wipe out
   * any existing counts for these keys.
   * <p>
   * To add to the counts of a collection of objects instead of replacing them,
   * use {@link #incrementCounts(Collection,int)}.
   */
  public void setCounts(Collection<E> keys, int count) {
    for (E key : keys) {
      setCount(key, count);
    }
  }

  /**
   * Adds the given count to the current count for the given key. If the key
   * hasn't been seen before, it is assumed to have count 0, and thus this
   * method will set its count to the given amount. Negative increments are
   * equivalent to calling {@code decrementCount}.
   * <p>
   * To more conveniently increment the count by 1, use
   * {@link #incrementCount(Object)}.
   * <p>
   * To set a count to a specific value instead of incrementing it, use
   * {@link #setCount(Object,int)}.
   */
  public int incrementCount(E key, int count) {
    if (tempMInteger == null) {
      tempMInteger = new MutableInteger();
    }

    MutableInteger oldMInteger = map.put(key, tempMInteger);
    totalCount += count;
    if (oldMInteger != null) {
      count += oldMInteger.intValue();
    }
    tempMInteger.set(count);
    tempMInteger = oldMInteger;

    return count;
  }

  /**
   * Adds 1 to the count for the given key. If the key hasn't been seen
   * before, it is assumed to have count 0, and thus this method will set
   * its count to 1.
   * <p>
   * To increment the count by a value other than 1, use
   * {@link #incrementCount(Object,int)}.
   * <p>
   * To set a count to a specific value instead of incrementing it, use
   * {@link #setCount(Object,int)}.
   */
  @Override
  public double incrementCount(E key) {
    return incrementCount(key, 1);
  }

  /**
   * Adds the given count to the current counts for each of the given keys.
   * If any of the keys haven't been seen before, they are assumed to have
   * count 0, and thus this method will set their counts to the given
   * amount. Negative increments are equivalent to calling {@code decrementCounts}.
   * <p>
   * To more conveniently increment the counts of a collection of objects by
   * 1, use {@link #incrementCounts(Collection)}.
   * <p>
   * To set the counts of a collection of objects to a specific value instead
   * of incrementing them, use {@link #setCounts(Collection,int)}.
   */
  public void incrementCounts(Collection<E> keys, int count) {
    for (E key : keys) {
      incrementCount(key, count);
    }
  }

  /**
   * Adds 1 to the counts for each of the given keys. If any of the keys
   * haven't been seen before, they are assumed to have count 0, and thus
   * this method will set their counts to 1.
   * <p>
   * To increment the counts of a collection of object by a value other
   * than 1, use {@link #incrementCounts(Collection,int)}.
   * <p>
   * To set the counts of a collection of objects  to a specific value instead
   * of incrementing them, use  {@link #setCounts(Collection,int)}.
   */
  public void incrementCounts(Collection<E> keys) {
    incrementCounts(keys, 1);
  }

  /**
   * Subtracts the given count from the current count for the given key.
   * If the key hasn't been seen before, it is assumed to have count 0, and
   * thus this  method will set its count to the negative of the given amount.
   * Negative increments are equivalent to calling {@code incrementCount}.
   * <p>
   * To more conveniently decrement the count by 1, use
   * {@link #decrementCount(Object)}.
   * <p>
   * To set a count to a specifc value instead of decrementing it, use
   * {@link #setCount(Object,int)}.
   */
  public int decrementCount(E key, int count) {
    return incrementCount(key, -count);
  }

  /**
   * Subtracts 1 from the count for the given key. If the key hasn't been
   * seen  before, it is assumed to have count 0, and thus this method will
   * set its count to -1.
   * <p>
   * To decrement the count by a value other than 1, use
   * {@link #decrementCount(Object,int)}.
   * <p>
   * To set a count to a specifc value instead of decrementing it, use
   * {@link #setCount(Object,int)}.
   */
  @Override
  public double decrementCount(E key) {
    return decrementCount(key, 1);
  }

  /**
   * Subtracts the given count from the current counts for each of the given keys.
   * If any of the keys haven't been seen before, they are assumed to have
   * count 0, and thus this method will set their counts to the negative of the given
   * amount. Negative increments are equivalent to calling {@code incrementCount}.
   * <p>
   * To more conveniently decrement the counts of a collection of objects by
   * 1, use {@link #decrementCounts(Collection)}.
   * <p>
   * To set the counts of a collection of objects to a specific value instead
   * of decrementing them, use {@link #setCounts(Collection,int)}.
   */
  public void decrementCounts(Collection<E> keys, int count) {
    incrementCounts(keys, -count);
  }

  /**
   * Subtracts 1 from the counts of each of the given keys. If any of the keys
   * haven't been seen before, they are assumed to have count 0, and thus
   * this method will set their counts to -1.
   * <p>
   * To decrement the counts of a collection of object by a value other
   * than 1, use {@link #decrementCounts(Collection,int)}.
   * <p>
   * To set the counts of a collection of objects  to a specifc value instead
   * of decrementing them, use  {@link #setCounts(Collection,int)}.
   */
  public void decrementCounts(Collection<E> keys) {
    decrementCounts(keys, 1);
  }

  /**
   * Adds the counts in the given Counter to the counts in this Counter.
   * <p>
   * To copy the values from another Counter rather than adding them, use
   */
  public void addAll(IntCounter<E> counter) {
    for (E key : counter.keySet()) {
      int count = counter.getIntCount(key);
      incrementCount(key, count);
    }
  }

  /**
   * Subtracts the counts in the given Counter from the counts in this Counter.
   * <p>
   * To copy the values from another Counter rather than subtracting them, use
   */
  public void subtractAll(IntCounter<E> counter) {
    for (E key : map.keySet()) {
      decrementCount(key, counter.getIntCount(key));
    }
  }

  // MAP LIKE OPERATIONS

  public boolean containsKey(E key) {
    return map.containsKey(key);
  }

  /**
   * Removes the given key from this Counter. Its count will now be 0 and it
   * will no longer be considered previously seen.
   */
  public double remove(E key) {
    totalCount -= getCount(key); // subtract removed count from total (may be 0)
    MutableInteger val = map.remove(key);
    if (val == null) {
      return Double.NaN;
    } else {
      return val.doubleValue();
    }
  }

  /**
   * Removes all the given keys from this Counter.
   */
  public void removeAll(Collection<E> c) {
    for (E key : c) {
      remove(key);
    }
  }

  /**
   * Removes all counts from this Counter.
   */
  public void clear() {
    map.clear();
    totalCount = 0;
  }

  public int size() {
    return map.size();
  }

  public boolean isEmpty() {
    return size() == 0;
  }

  public Set<E> keySet() {
    return map.keySet();
  }

  /**
   * Returns a view of the doubles in this map.  Can be safely modified.
   */
  public Set<Map.Entry<E,Double>> entrySet() {
    return new AbstractSet<Map.Entry<E,Double>>() {
      @Override
      public Iterator<Entry<E, Double>> iterator() {
        return new Iterator<Entry<E,Double>>() {
          final Iterator<Entry<E,MutableInteger>> inner = map.entrySet().iterator();

          public boolean hasNext() {
            return inner.hasNext();
          }

          public Entry<E, Double> next() {
            return new Map.Entry<E,Double>() {
              final Entry<E,MutableInteger> e = inner.next();

              public E getKey() {
                return e.getKey();
              }

              public Double getValue() {
                return e.getValue().doubleValue();
              }

              public Double setValue(Double value) {
                final double old = e.getValue().doubleValue();
                e.getValue().set(value.intValue());
                totalCount = totalCount - (int)old + value.intValue();
                return old;
              }
            };
          }

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

  // OBJECT STUFF

  @SuppressWarnings("unchecked")
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IntCounter)) {
      return false;
    }

    final IntCounter counter = (IntCounter) o;

    return map.equals(counter.map);
  }

  @Override
  public int hashCode() {
    return map.hashCode();
  }

  @Override
  public String toString() {
    return map.toString();
  }


  @SuppressWarnings("unchecked")
  public String toString(NumberFormat nf, String preAppend, String postAppend, String keyValSeparator, String itemSeparator) {
    StringBuilder sb = new StringBuilder();
    sb.append(preAppend);
    List<E> list = new ArrayList<>(map.keySet());
    try {
      Collections.sort((List)list); // see if it can be sorted
    } catch (Exception e) {
    }
    for (Iterator<E> iter = list.iterator(); iter.hasNext();) {
      Object key = iter.next();
      MutableInteger d = map.get(key);
      sb.append(key + keyValSeparator);
      sb.append(nf.format(d));
      if (iter.hasNext()) {
        sb.append(itemSeparator);
      }
    }
    sb.append(postAppend);
    return sb.toString();
  }


  @SuppressWarnings("unchecked")
  public String toString(NumberFormat nf) {
    StringBuilder sb = new StringBuilder();
    sb.append("{");
    List<E> list = new ArrayList<>(map.keySet());
    try {
      Collections.sort((List)list); // see if it can be sorted
    } catch (Exception e) {
    }
    for (Iterator<E> iter = list.iterator(); iter.hasNext();) {
      Object key = iter.next();
      MutableInteger d = map.get(key);
      sb.append(key + "=");
      sb.append(nf.format(d));
      if (iter.hasNext()) {
        sb.append(", ");
      }
    }
    sb.append("}");
    return sb.toString();
  }

  @Override
  public Object clone() {
    return new IntCounter<>(this);
  }

  // EXTRA CALCULATION METHODS

  /**
   * Removes all keys whose count is 0. After incrementing and decrementing
   * counts or adding and subtracting Counters, there may be keys left whose
   * count is 0, though normally this is undesirable. This method cleans up
   * the map.
   * <p>
   * Maybe in the future we should try to do this more on-the-fly, though it's
   * not clear whether a distinction should be made between "never seen" (i.e.
   * null count) and "seen with 0 count". Certainly there's no distinction in
   * getCount() but there is in containsKey().
   */
  public void removeZeroCounts() {
    map.keySet().removeIf(e -> getCount(e) == 0);
  }

  /**
   * Finds and returns the largest count in this Counter.
   */
  public int max() {
    int max = Integer.MIN_VALUE;
    for (E key : map.keySet()) {
      max = Math.max(max, getIntCount(key));
    }
    return max;
  }

  public double doubleMax() {
    return max();
  }

  /**
   * Finds and returns the smallest count in this Counter.
   */
  public int min() {
    int min = Integer.MAX_VALUE;
    for (E key : map.keySet()) {
      min = Math.min(min, getIntCount(key));
    }
    return min;
  }

  /**
   * Finds and returns the key in this Counter with the largest count.
   * Ties are broken by comparing the objects using the given tie breaking
   * Comparator, favoring Objects that are sorted to the front. This is useful
   * if the keys are numeric and there is a bias to prefer smaller or larger
   * values, and can be useful in other circumstances where random tie-breaking
   * is not desirable. Returns null if this Counter is empty.
   */
  public E argmax(Comparator<E> tieBreaker) {
    int max = Integer.MIN_VALUE;
    E argmax = null;
    for (E key : keySet()) {
      int count = getIntCount(key);
      if (argmax == null || count > max || (count == max && tieBreaker.compare(key, argmax) < 0)) {
        max = count;
        argmax = key;
      }
    }
    return argmax;
  }


  /**
   * Finds and returns the key in this Counter with the largest count.
   * Ties are broken according to the natural ordering of the objects.
   * This will prefer smaller numeric keys and lexicographically earlier
   * String keys. To use a different tie-breaking Comparator, use
   * {@link #argmax(Comparator)}. Returns null if this Counter is empty.
   */
  public E argmax() {
    return argmax(ErasureUtils.<Comparator<E>>uncheckedCast(naturalComparator));
  }

  /**
   * Finds and returns the key in this Counter with the smallest count.
   * Ties are broken by comparing the objects using the given tie breaking
   * Comparator, favoring Objects that are sorted to the front. This is useful
   * if the keys are numeric and there is a bias to prefer smaller or larger
   * values, and can be useful in other circumstances where random tie-breaking
   * is not desirable. Returns null if this Counter is empty.
   */
  public E argmin(Comparator<E> tieBreaker) {
    int min = Integer.MAX_VALUE;
    E argmin = null;
    for (E key : map.keySet()) {
      int count = getIntCount(key);
      if (argmin == null || count < min || (count == min && tieBreaker.compare(key, argmin) < 0)) {
        min = count;
        argmin = key;
      }
    }
    return argmin;
  }

  /**
   * Finds and returns the key in this Counter with the smallest count.
   * Ties are broken according to the natural ordering of the objects.
   * This will prefer smaller numeric keys and lexicographically earlier
   * String keys. To use a different tie-breaking Comparator, use
   * {@link #argmin(Comparator)}. Returns null if this Counter is empty.
   */
  public E argmin() {
    return argmin(ErasureUtils.<Comparator<E>>uncheckedCast(naturalComparator));
  }

  /**
   * Returns the set of keys whose counts are at or above the given threshold.
   * This set may have 0 elements but will not be null.
   */
  public Set<E> keysAbove(int countThreshold) {
    Set<E> keys = Generics.newHashSet();
    for (E key : map.keySet()) {
      if (getIntCount(key) >= countThreshold) {
        keys.add(key);
      }
    }
    return keys;
  }

  /**
   * Returns the set of keys whose counts are at or below the given threshold.
   * This set may have 0 elements but will not be null.
   */
  public Set<E> keysBelow(int countThreshold) {
    Set<E> keys = Generics.newHashSet();
    for (E key : map.keySet()) {
      if (getIntCount(key) <= countThreshold) {
        keys.add(key);
      }
    }
    return keys;
  }

  /**
   * Returns the set of keys that have exactly the given count.
   * This set may have 0 elements but will not be null.
   */
  public Set<E> keysAt(int count) {
    Set<E> keys = Generics.newHashSet();
    for (E key : map.keySet()) {
      if (getIntCount(key) == count) {
        keys.add(key);
      }
    }
    return keys;
  }

  /**
   * Comparator that uses natural ordering.
   * Returns 0 if o1 is not Comparable.
   */
  private static class NaturalComparator<T> implements Comparator<T> {
    public int compare(T o1, T o2) {
      if (o1 instanceof Comparable) {
        return ErasureUtils.<Comparable<T>>uncheckedCast(o1).compareTo(o2);
      }
      return 0; // soft-fail
    }
  }

  //
  // For compatibilty with the Counter interface
  //

  public Factory<Counter<E>> getFactory() {
    return new Factory<Counter<E>>() {
      private static final long serialVersionUID = 7470763055803428477L;

      public Counter<E> create() {
        return new IntCounter<>(getMapFactory());
      }
    };
  }

  public void setCount(E key, double value) {
    setCount(key, (int)value);
  }

  @Override
  public double incrementCount(E key, double value) {
    incrementCount(key, (int)value);
    return getCount(key);
  }

  public double totalCount() {
    return totalDoubleCount();
  }

  public Collection<Double> values() {
    return new AbstractCollection<Double>() {
      @Override
      public Iterator<Double> iterator() {
        return new Iterator<Double>() {
          Iterator<MutableInteger> inner = map.values().iterator();

          public boolean hasNext() {
            return inner.hasNext();
          }

          public Double next() {
            return inner.next().doubleValue();
          }

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

  public Iterator<E> iterator() {
    return keySet().iterator();
  }

  /**
   * {@inheritDoc}
   */
  public void prettyLog(RedwoodChannels channels, String description) {
    PrettyLogger.log(channels, description, Counters.asMap(this));
  }
}
