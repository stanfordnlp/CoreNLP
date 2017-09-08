// Stanford JavaNLP support classes
// Copyright (c) 2004-2008 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//    java-nlp-support@lists.stanford.edu
//    http://nlp.stanford.edu/software/

package old.edu.stanford.nlp.stats;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.text.NumberFormat;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;

import old.edu.stanford.nlp.math.ArrayMath;
import old.edu.stanford.nlp.math.SloppyMath;
import old.edu.stanford.nlp.util.BinaryHeapPriorityQueue;
import old.edu.stanford.nlp.util.CollectionUtils;
import old.edu.stanford.nlp.util.ErasureUtils;
import old.edu.stanford.nlp.util.Factory;
import old.edu.stanford.nlp.util.FixedPrioritiesPriorityQueue;
import old.edu.stanford.nlp.util.Function;
import old.edu.stanford.nlp.util.Index;
import old.edu.stanford.nlp.util.PriorityQueue;
import old.edu.stanford.nlp.util.Sets;
import old.edu.stanford.nlp.util.Pair;
import old.edu.stanford.nlp.util.StringUtils;


/**
 * Static methods for operating on {@link Counter}s.
 * <p>
 * All methods that change their arguments change the <i>first</i> argument
 * (only), and have "InPlace" in their name.
 * This class also provides access
 * to Comparators that can be used to sort the keys or entries of this Counter
 * by the counts, in either ascending or descending order.
 *
 * @author Galen Andrew (galand@cs.stanford.edu)
 * @author Jeff Michels (jmichels@stanford.edu)
 * @author dramage
 * @author cer
 * @author Christopher Manning
 */
public class Counters {

  private static final double LOG_E_2 = Math.log(2.0);

  private Counters() {} // only static methods

  //
  // Log arithmetic operations
  //

  /**
   * Returns ArrayMath.logSum of the values in this counter.
   *
   * @param c Argument counter (which is not modified)
   * @return ArrayMath.logSum of the values in this counter.
   */
  public static <E> double logSum(Counter<E> c) {
    return ArrayMath.logSum(ArrayMath.unbox(c.values()));
  }

  /** Transform log space values into a probability distribution in place.
   *  On the assumption that the values in the Counter are in log space,
   *  this method calculates their sum, and then subtracts the log of
   *  their sum from each element.  That is, if a counter has keys
   *  c1, c2, c3 with values v1, v2, v3, the value of c1 becomes
   *  v1 - log(e^v1 + e^v2 + e^v3).  After this, e^v1 + e^v2 + e^v3 = 1.0,
   *  so Counters.logSum(c) = 0.0 (approximately).
   *
   *  @param c The Counter to log normalize in place
   */
  @SuppressWarnings({"UnnecessaryUnboxing"})
  public static <E> void logNormalizeInPlace(Counter<E> c) {
    double logsum = logSum(c);
    // for (E key : c.keySet()) {
    //   c.incrementCount(key, -logsum);
    // }
    // This should be faster
    for (Entry<E,Double> e : c.entrySet()) {
      e.setValue(e.getValue().doubleValue() - logsum);
    }
  }

  //
  // Query operations
  //


  /**
   * Returns the value of the maximum entry in this counter.
   * This is also the Linfinity norm.
   * An empty counter is given a max value of Double.NEGATIVE_INFINITY.
   *
   * @param c The Counter to find the max of
   * @return The maximum value of the Counter
   */
  public static <E> double max(Counter<E> c) {
    double max = Double.NEGATIVE_INFINITY;
    for (double v : c.values()) {
      max = Math.max(max, v);
    }
    return max;
  }

  /**
   * Takes in a Collection of something and makes a counter, incrementing once
   * for each object in the collection.
   *
   * @param c The Collection to turn into a counter
   * @return The counter made out of the collection
   */
  public static <E> Counter<E> asCounter(Collection<E> c) {
    Counter<E> count = new ClassicCounter<E>();
    for(E elem: c){
      count.incrementCount(elem);
    }
    return count;
  }


  /**
   * Returns the value of the smallest entry in this counter.
   *
   * @param c The Counter (not modified)
   * @return The minimum value in the Counter
   */
  public static <E> double min(Counter<E> c) {
    double min = Double.POSITIVE_INFINITY;
    for (double v : c.values()) {
      min = Math.min(min, v);
    }
    return min;
  }

  /**
   * Finds and returns the key in the Counter with the largest count.
   * Returning null if count is empty.
   *
   * @param c The Counter
   * @return The key in the Counter with the largest count.
   */
  public static <E> E argmax(Counter<E> c) {
    double max = Double.NEGATIVE_INFINITY;
    E argmax = null;
    for (E key : c.keySet()) {
      double count = c.getCount(key);
      if (argmax == null || count > max) {// || (count == max && tieBreaker.compare(key, argmax) < 0)) {
        max = count;
        argmax = key;
      }
    }
    return argmax;
  }

  /**
   * Finds and returns the key in this Counter with the smallest count.
   *
   * @param c The Counter
   * @return The key in the Counter with the smallest count.
   */
  public static <E> E argmin(Counter<E> c) {
    double min = Double.POSITIVE_INFINITY;
    E argmin = null;

    for (E key : c.keySet()) {
      double count = c.getCount(key);
      if (argmin == null || count < min) {// || (count == min && tieBreaker.compare(key, argmin) < 0)) {
        min = count;
        argmin = key;
      }
    }
    return argmin;
  }


  // TODO: Reinstate versions of argmax and argmin with stable tie-breaking.

  /**
   * Returns the mean of all the counts (totalCount/size).
   *
   * @param c The Counter to find the mean of.
   * @return The mean of all the counts (totalCount/size).
   */
  public static <E> double mean(Counter<E> c) {
    return c.totalCount() / c.size();
  }

  //
  // In-place arithmetic
  //

  /**
   * Sets each value of target to be target[k]+scale*arg[k] for
   * all keys k in target.
   *
   * @param target A Counter that is modified
   * @param arg The Counter whose contents are added to target
   * @param scale How the arg Counter is scaled before being added
   */
  // TODO: Rewrite to use arg.entrySet()
  public static <E> void addInPlace(Counter<E> target, Counter<E> arg, double scale) {
    for (E key : arg.keySet()) {
      target.incrementCount(key, scale * arg.getCount(key));
    }
  }

  /**
   * Sets each value of target to be target[k]+arg[k] for all keys k in target.
   */
  public static <E> void addInPlace(Counter<E> target, Counter<E> arg) {
    for (E key : arg.keySet()) {
      target.incrementCount(key, arg.getCount(key));
    }
  }

  /**
   * For all keys (u,v) in arg, sets target[u,v] to be target[u,v] + scale * arg[u,v]
   * @param <T1>
   * @param <T2>
   */
  public static <T1, T2> void addInPlace(TwoDimensionalCounter<T1, T2> target, TwoDimensionalCounter<T1, T2> arg, double scale) {
    for (T1 outer : arg.firstKeySet())
      for (T2 inner : arg.secondKeySet()) {
        target.incrementCount(outer, inner, scale * arg.getCount(outer, inner));
      }
  }

  /**
   * For all keys (u,v) in arg, sets target[u,v] to be target[u,v] + arg[u,v]
   * @param <T1>
   * @param <T2>
   */
  public static <T1, T2> void addInPlace(TwoDimensionalCounter<T1, T2> target, TwoDimensionalCounter<T1, T2> arg) {
    for (T1 outer : arg.firstKeySet())
      for (T2 inner : arg.secondKeySet()) {
        target.incrementCount(outer, inner, arg.getCount(outer, inner));
      }
  }

  /**
   * Sets each value of target to be target[k]-arg[k] for all keys k in target.
   */
  public static <E> void subtractInPlace(Counter<E> target, Counter<E> arg) {
    for (E key : arg.keySet()) {
      target.decrementCount(key, arg.getCount(key));
    }
  }

  /**
   * Divides every non-zero count in target by the corresponding value in
   * the denominator Counter.  Beware that this can give NaN values for zero
   * counts in the denominator counter!
   */
  public static <E> void divideInPlace(Counter<E> target, Counter<E> denominator) {
    for (E key : target.keySet()) {
      target.setCount(key, target.getCount(key) / denominator.getCount(key));
    }
  }

  /**
   * Multiplies every count in target by the corresponding value in
   * the term Counter.
   */
  public static <E> void dotProductInPlace(Counter<E> target, Counter<E> term) {
    for (E key : target.keySet()) {
      target.setCount(key, target.getCount(key) * term.getCount(key));
    }
  }

  /**
   * Divides each value in target by the given divisor, in place.
   *
   * @param target The values in this Counter will be changed throught by
   *     the multiplier
   * @param divisor The number by which to change each number in the
   *     Counter
   * @return The target Counter is returned (for easier method chaining)
   */
  public static <E> Counter<E> divideInPlace(Counter<E> target, double divisor) {
    for (Entry<E, Double> entry : target.entrySet()) {
      target.setCount(entry.getKey(), entry.getValue() / divisor);
    }
    return target;
  }


  /**
   * Multiplies each value in target by the given multiplier, in place.
   *
   * @param target The values in this Counter will be changed throught by
   *     the multiplier
   * @param multiplier The number by which to change each number in the
   *     Counter
   */
  public static <E> Counter<E> multiplyInPlace(Counter<E> target, double multiplier) {
    for (Entry<E, Double> entry : target.entrySet()) {
      target.setCount(entry.getKey(), entry.getValue() * multiplier);
    }
    return target;
  }


  /**
   * Normalizes the target counter in-place, so the sum of the
   * resulting values equals 1.
   * @param <E>
   */
  public static <E> void normalize(Counter<E> target) {
    multiplyInPlace(target, 1.0 / target.totalCount());
  }

  public static <E> void logInPlace(Counter<E> target) {
    for(E key : target.keySet()) {
      target.setCount(key,Math.log(target.getCount(key)));
    }
  }

  //
  // Selection Operators
  //

  /**
   * Removes all entries from c except for the top <code>num</code>
   */
  public static <E> void retainTop(Counter<E> c, int num) {
    int numToPurge = c.size()-num;
    if (numToPurge <=0) {
      return;
    }

    List<E> l = Counters.toSortedList(c);
    Collections.reverse(l);
    for (int i=0; i<numToPurge; i++) {
      c.remove(l.get(i));
    }
  }

  /**
   * Removes all entries with 0 count in the counter, returning the
   * set of removed entries.
   */
  public static <E> Set<E> retainNonZeros(Counter<E> counter) {
    Set<E> removed = new HashSet<E>();
    for (E key : counter.keySet()) {
      if (counter.getCount(key) == 0.0) {
        removed.add(key);
      }
    }
    for (E key : removed) {
      counter.remove(key);
    }
    return removed;
  }

  /**
   * Removes all entries with counts below the given threshold, returning the
   * set of removed entries.
   *
   * @param counter         The counter.
   * @param countThreshold  The minimum count for an entry to be kept. Entries
   *                        (strictly) less than this threshold are discarded.
   * @return                The set of discarded entries.
   */
  public static <E> Set<E> retainAbove(Counter<E> counter, double countThreshold) {
    Set<E> removed = new HashSet<E>();
    for (E key : counter.keySet()) {
      if (counter.getCount(key) < countThreshold) {
        removed.add(key);
      }
    }
    for (E key: removed) {
      counter.remove(key);
    }
    return removed;
  }

  /**
   * Returns the set of keys whose counts are at or above the given threshold.
   * This set may have 0 elements but will not be null.
   *
   * @param c The Counter to examine
   * @param countThreshold Items equal to or above this number are kept
   * @return A (non-null) Set of keys whose counts are at or above the given
   *     threshold.
   */
  public static <E> Set<E> keysAbove(Counter<E> c, double countThreshold) {
    Set<E> keys = new HashSet<E>();
    for (E key : c.keySet()) {
      if (c.getCount(key) >= countThreshold) {
        keys.add(key);
      }
    }
    return (keys);
  }

  /**
   * Returns the set of keys whose counts are at or below the given threshold.
   * This set may have 0 elements but will not be null.
   */
  public static <E> Set<E> keysBelow(Counter<E> c, double countThreshold) {
    Set<E> keys = new HashSet<E>();
    for (E key : c.keySet()) {
      if (c.getCount(key) <= countThreshold) {
        keys.add(key);
      }
    }
    return (keys);
  }

  /**
   * Returns the set of keys that have exactly the given count.
   * This set may have 0 elements but will not be null.
   */
  public static <E> Set<E> keysAt(Counter<E> c, double count) {
    Set<E> keys = new HashSet<E>();
    for (E key : c.keySet()) {
      if (c.getCount(key) == count) {
        keys.add(key);
      }
    }
    return (keys);
  }


  //
  // Transforms
  //

  /**
    * Returns the counter with keys modified according to function F. Eager evaluation.
    */
  public static <T1,T2> Counter<T2> transform(Counter<T1> c, Function<T1,T2> f) {
     Counter<T2> c2 = new ClassicCounter<T2>();
     for(T1 key : c.keySet()) {
        c2.setCount(f.apply(key),c.getCount(key));
     }
     return c2;
  }


  //
  // Conversion to other types
  //

  /**
   * Returns a comparator backed by this counter: two objects are compared
   * by their associated values stored in the counter.
   * This comparator returns keys by ascending numeric value.
   * Note that this ordering
   * is not fixed, but depends on the mutable values stored in the Counter.
   * Doing this comparison does not depend on the type of the key, since it
   * uses the numeric value, which is always Comparable.
   *
   * @param counter The Counter whose values are used for ordering the keys
   * @return A Comparator using this ordering
   */
  public static <E> Comparator<E> toComparator(final Counter<E> counter) {
    return new Comparator<E>() {
      public int compare(E o1, E o2) {
        return Double.compare(counter.getCount(o1), counter.getCount(o2));
      }
    };
  }

  /**
   * Returns a comparator backed by this counter: two objects are compared
   * by their associated values stored in the counter.
   * This comparator returns keys by descending numeric value.
   * Note that this ordering
   * is not fixed, but depends on the mutable values stored in the Counter.
   * Doing this comparison does not depend on the type of the key, since it
   * uses the numeric value, which is always Comparable.
   *
   * @param counter The Counter whose values are used for ordering the keys
   * @return A Comparator using this ordering
   */
  public static <E> Comparator<E> toComparatorDescending(final Counter<E> counter) {
    return new Comparator<E>() {
      public int compare(E o1, E o2) {
        return Double.compare(counter.getCount(o2), counter.getCount(o1));
      }
    };
  }

  /**
   * Returns a comparator suitable for sorting this Counter's keys or entries
   * by their respective value or magnitude (by absolute value).
   * If <tt>ascending</tt> is true, smaller magnitudes will
   * be returned first, otherwise higher magnitudes will be returned first.
   * <p/>
   * Sample usage:
   * <pre>
   * Counter c = new Counter();
   * // add to the counter...
   * List biggestKeys = new ArrayList(c.keySet());
   * Collections.sort(biggestAbsKeys, Counters.comparator(c, false, true));
   * List smallestEntries = new ArrayList(c.entrySet());
   * Collections.sort(smallestEntries, Counters.comparator(c, true, false));
   * </pre>
   */
  public static <E> Comparator<E> toComparator(final Counter<E> counter,
                                               final boolean ascending,
                                               final boolean useMagnitude) {
    return new Comparator<E>() {
      public int compare(E o1, E o2) {
        if (ascending) {
          if (useMagnitude) {
            return Double.compare(Math.abs(counter.getCount(o1)), Math.abs(counter.getCount(o2)));
          } else {
            return Double.compare(counter.getCount(o1), counter.getCount(o2));
          }
        } else {
          // Descending
          if (useMagnitude) {
            return Double.compare(Math.abs(counter.getCount(o2)), Math.abs(counter.getCount(o1)));
          } else {
            return Double.compare(counter.getCount(o2), counter.getCount(o1));
          }
        }
      }
    };
  }

  /**
   *  A List of the keys in c, sorted from highest count to lowest.
   *
   * @return A List of the keys in c, sorted from highest count to lowest.
   */
  public static <E> List<E> toSortedList(Counter<E> c) {
    List<E> l = new ArrayList<E>(c.keySet());
    Collections.sort(l, toComparator(c));
    Collections.reverse(l);
    return l;
  }

  public static <E> List<Pair<E,Double>> toDescendingMagnitudeSortedListWithCounts(Counter<E> c) {
    List<E> keys = new ArrayList<E>(c.keySet());
    Collections.sort(keys, toComparator(c, false, true));
    List<Pair<E,Double>> l = new ArrayList<Pair<E,Double>>(keys.size());

    for (E key : keys) {
      l.add(new Pair<E,Double>(key, c.getCount(key)));
    }

    return l;
  }

  /**
   * A List of the keys in c, sorted from highest count to lowest, paired with counts
   *
   * @return A List of the keys in c, sorted from highest count to lowest.
   */
  public static <E> List<Pair<E,Double>> toSortedListWithCounts(Counter<E> c) {
    List<Pair<E,Double>> l = new ArrayList<Pair<E, Double>>(c.size());
    for(E e: c.keySet()) {
      l.add(new Pair<E,Double>(e,c.getCount(e)));
    }
    // descending order
    Collections.sort(l, new Comparator<Pair<E,Double>>() {
      public int compare(Pair<E,Double> a, Pair<E,Double> b) {
        return Double.compare(b.second,a.second);
      }
    });
    return l;
  }


  /**
   * Returns a {@link edu.stanford.nlp.util.PriorityQueue} whose elements
   * are the keys of Counter c,
   * and the score of each key in c becomes its priority.
   *
   * @param c Input Counter
   * @return A PriorityQueue where the count is a key's priority
   */
  // TODO: rewrite to use entrySet()
  public static <E> PriorityQueue<E> toPriorityQueue(Counter<E> c) {
    PriorityQueue<E> queue = new BinaryHeapPriorityQueue<E>();
    for (E key : c.keySet()) {
      double count = c.getCount(key);
      queue.add(key, count);
    }
    return queue;
  }


  //
  // Other Utilities
  //

  /**
   * Returns a Counter that is the union of the two Counters passed in (counts are added).
   *
   * @return A Counter that is the union of the two Counters passed in (counts are added).
   */
  @SuppressWarnings("unchecked")
  public static <E, C extends Counter<E>> C union(C c1, C c2) {
    C result = (C)c1.getFactory().create();
    addInPlace(result, c1);
    addInPlace(result, c2);
    return result;
  }

  /**
   * Returns a counter that is the intersection of c1 and c2.  If both c1 and c2 contain a
   * key, the min of the two counts is used.
   *
   * @return A counter that is the intersection of c1 and c2
   */
  public static <E> Counter<E> intersection(Counter<E> c1, Counter<E> c2) {
    Counter<E> result = c1.getFactory().create();
    for (E key : Sets.union(c1.keySet(), c2.keySet())) {
      double count1 = c1.getCount(key);
      double count2 = c2.getCount(key);
      double minCount = (count1 < count2 ? count1 : count2);
      if (minCount > 0) {
        result.setCount(key, minCount);
      }
    }
    return result;
  }

  /**
   * Returns the Jaccard Coefficient of the two counters. Calculated as
   * |c1 intersect c2| / ( |c1| + |c2| - |c1 intersect c2|
   *
   * @return The Jaccard Coefficient of the two counters
   */
  public static <E> double jaccardCoefficient(Counter<E> c1, Counter<E> c2) {
    double minCount = 0.0, maxCount = 0.0;
    for (E key : Sets.union(c1.keySet(), c2.keySet())) {
      double count1 = c1.getCount(key);
      double count2 = c2.getCount(key);
      minCount += (count1 < count2 ? count1 : count2);
      maxCount += (count1 > count2 ? count1 : count2);
    }
    return minCount / maxCount;
  }

  /**
   * Returns the product of c1 and c2.
   *
   * @return The product of c1 and c2.
   */
  public static <E> Counter<E> product(Counter<E> c1, Counter<E> c2) {
    Counter<E> result = c1.getFactory().create();
    for (E key : Sets.intersection(c1.keySet(), c2.keySet())) {
      result.setCount(key, c1.getCount(key) * c2.getCount(key));
    }
    return result;
  }

  /**
   * Returns the product of c1 and c2.
   *
   * @return The product of c1 and c2.
   */
  public static <E> double dotProduct(Counter<E> c1, Counter<E> c2) {
    double dotProd = 0.0;
    for (E key : c1.keySet()) {
      double count1 = c1.getCount(key);
      if (Double.isNaN(count1) || Double.isInfinite(count1)) {
        throw new RuntimeException("Counters.dotProduct infinite or NaN value for key: " + key+ '\t' + c1.getCount(key) + '\t' + c2.getCount(key));
      }
      if (count1 != 0.0) {
        double count2 = c2.getCount(key);
        if (Double.isNaN(count2) || Double.isInfinite(count2)) {
          throw new RuntimeException("Counters.dotProduct infinite or NaN value for key: " + key+ '\t' + c1.getCount(key) + '\t' + c2.getCount(key));
        }
        if (count2 != 0.0) {
          // this is the inner product
          dotProd += (count1 * count2);
        }
      }
    }
    return dotProd;
  }


  /**
   * Returns |c1 - c2|.
   *
   * @return The difference between sets c1 and c2.
   */
  public static <E> Counter<E> absoluteDifference(Counter<E> c1, Counter<E> c2) {
    Counter<E> result = c1.getFactory().create();
    for (E key : Sets.union(c1.keySet(), c2.keySet())) {
      double newCount = Math.abs(c1.getCount(key) - c2.getCount(key));
      if (newCount > 0) {
        result.setCount(key, newCount);
      }
    }
    return result;
  }

  /**
   * Returns c1 divided by c2.  Note that this can create NaN if c1 has non-zero counts for keys that
   * c2 has zero counts.
   *
   * @return c1 divided by c2.
   */
  public static <E> Counter<E> division(Counter<E> c1, Counter<E> c2) {
    Counter<E> result = c1.getFactory().create();
    for (E key : Sets.union(c1.keySet(), c2.keySet())) {
      result.setCount(key, c1.getCount(key) / c2.getCount(key));
    }
    return result;
  }

  /**
   * Calculates the entropy of the given counter (in bits). This method internally
   * uses normalized counts (so they sum to one), but the value returned is
   * meaningless if some of the counts are negative.
   *
   * @return The entropy of the given counter (in bits)
   */
  public static <E> double entropy(Counter<E> c) {
    double entropy = 0.0;
    double total = c.totalCount();
    for (E key : c.keySet()) {
      double count = c.getCount(key);
      if (count == 0) {
        continue; // 0.0 doesn't add entropy but may cause -Inf
      }
      count /= total; // use normalized count
      entropy -= count * (Math.log(count) / LOG_E_2);
    }
    return entropy;
  }

  /**
   * Note that this implementation doesn't normalize the "from" Counter.
   * It does, however, normalize the "to" Counter.
   * Result is meaningless if any of the counts are negative.
   *
   * @return The cross entropy of H(from, to)
   */
  public static <E> double crossEntropy(Counter<E> from, Counter<E> to) {
    double tot2 = to.totalCount();
    double result = 0.0;
    for (E key : from.keySet()) {
      double count1 = from.getCount(key);
      if (count1 == 0.0) {
        continue;
      }
      double count2 = to.getCount(key);
      double logFract = Math.log(count2 / tot2);
      if (logFract == Double.NEGATIVE_INFINITY) {
        return Double.NEGATIVE_INFINITY; // can't recover
      }
      result += count1 * (logFract / LOG_E_2); // express it in log base 2
    }
    return result;
  }

  /**
   * Calculates the KL divergence between the two counters.
   * That is, it calculates KL(from || to). This method internally
   * uses normalized counts (so they sum to one), but the value returned is
   * meaningless if any of the counts are negative.
   * In other words, how well can c1 be represented by c2.
   * if there is some value in c1 that gets zero prob in c2, then return positive infinity.
   *
   * @return The KL divergence between the distributions
   */
  public static <E> double klDivergence(Counter<E> from, Counter<E> to) {
    double result = 0.0;
    double tot = (from.totalCount());
    double tot2 = (to.totalCount());
    // System.out.println("tot is " + tot + " tot2 is " + tot2);
    for (E key : from.keySet()) {
      double num = (from.getCount(key));
      if (num == 0) {
        continue;
      }
      num /= tot;
      double num2 = (to.getCount(key));
      num2 /= tot2;
      // System.out.println("num is " + num + " num2 is " + num2);
      double logFract = Math.log(num / num2);
      if (logFract == Double.NEGATIVE_INFINITY) {
        return Double.NEGATIVE_INFINITY; // can't recover
      }
      result += num * (logFract / LOG_E_2); // express it in log base 2
    }
    return result;
  }

  /**
   * Calculates the Jensen-Shannon divergence between the two counters.
   * That is, it calculates 1/2 [KL(c1 || avg(c1,c2)) + KL(c2 || avg(c1,c2))] .
   *
   * @return The Jensen-Shannon divergence between the distributions
   */
  public static <E> double jensenShannonDivergence(Counter<E> c1, Counter<E> c2) {
    Counter<E> average = average(c1, c2);
    double kl1 = klDivergence(c1, average);
    double kl2 = klDivergence(c2, average);
    return (kl1 + kl2) / 2.0;
  }

  /**
   * Calculates the skew divergence between the two counters.
   * That is, it calculates KL(c1 || (c2*skew + c1*(1-skew))) .
   * In other words, how well can c1 be represented by a "smoothed" c2.
   *
   * @return The skew divergence between the distributions
   */
  public static <E> double skewDivergence(Counter<E> c1, Counter<E> c2, double skew) {
    Counter<E> average = linearCombination(c2, skew, c1, (1.0 - skew));
    return klDivergence(c1, average);
  }

  /**
   * Return the l2 norm (Euclidean vector length) of a Counter.
   * <i>Implementation note:</i> The method name favors legibility of the
   * L over the convention of using lowercase names for methods.
   *
   * @param c The Counter
   * @return Its length
   */
  public static <E, C extends Counter<E>> double L2Norm(C c) {
    double lenSq = 0.0;
    for (E key : c.keySet()) {
      double count = c.getCount(key);
      if (count != 0.0) {
        lenSq += (count * count);
      }
    }
    return Math.sqrt(lenSq);
  }


  
  /** L2 normalize a counter.
   *
   * @param c The {@link Counter} to be L2 normalized.  This counter is
   *   not modified.
   * @return A new l2-normalized Counter based on c.
   */
  public static <E,C extends Counter<E>> C L2Normalize(C c) {
    return scale(c, 1.0/ L2Norm(c));
  }

  /**
   * For counters with large # of entries, this scales down each entry in the sum,
   * to prevent an extremely large sum from building up and overwhelming the
   * max double.  This may also help reduce error by preventing loss of SD's with 
   * extremely large values. 
   * @param <E>
   * @param <C>
   */
  public static <E, C extends Counter<E>> double saferL2Norm(C c) {
    double maxVal = 0.0;
    for (E key : c.keySet()) {
      double value = Math.abs(c.getCount(key));
      if (value > maxVal)
          maxVal = value;
    }
    double sqrSum = 0.0;
    for (E key : c.keySet()) {
      double count = c.getCount(key);
      sqrSum += Math.pow(count/maxVal, 2);
    }
    return maxVal * Math.sqrt(sqrSum);
  }
  
  /** L2 normalize a counter, using the "safer" L2 normalizer.
  *
  * @param c The {@link Counter} to be L2 normalized.  This counter is
  *   not modified.
  * @return A new l2-normalized Counter based on c.
  */
 public static <E,C extends Counter<E>> C saferL2Normalize(C c) {
   return scale(c, 1.0/ saferL2Norm(c));
 }
  
  public static <E> double cosine(Counter<E> c1, Counter<E> c2) {
    double dotProd = 0.0;
    double lsq1 = 0.0;
    double lsq2 = 0.0;
    for (E key : c1.keySet()) {
      double count1 = c1.getCount(key);
      if (count1 != 0.0) {
        lsq1 += (count1 * count1);
        double count2 = c2.getCount(key);
        if (count2 != 0.0) {
          // this is the inner product
          dotProd += (count1 * count2);
        }
      }
    }
    for (E key : c2.keySet()) {
      double count2 = c2.getCount(key);
      if (count2 != 0.0) {
        lsq2 += (count2 * count2);
      }
    }
    if (lsq1 != 0.0 && lsq2 != 0.0) {
      double denom = (Math.sqrt(lsq1) * Math.sqrt(lsq2));
      return dotProd / denom;
    }
    return 0.0;
  }

  /**
   * Returns a new Counter with counts averaged from the two given Counters.
   * The average Counter will contain the union of keys in both
   * source Counters, and each count will be the average of the two source
   * counts for that key, where as usual a missing count in one Counter
   * is treated as count 0.
   *
   * @return A new counter with counts that are the mean of the resp. counts
   *         in the given counters.
   */
  public static <E> Counter<E> average(Counter<E> c1, Counter<E> c2) {
    Counter<E> average = c1.getFactory().create();
    Set<E> allKeys = new HashSet<E>(c1.keySet());
    allKeys.addAll(c2.keySet());
    for (E key : allKeys) {
      average.setCount(key, (c1.getCount(key) + c2.getCount(key)) * 0.5);
    }
    return average;
  }


  /**
   * Returns a Counter which is a weighted average of c1 and c2. Counts from c1
   * are weighted with weight w1 and counts from c2 are weighted with w2.
   */
  public static <E> Counter<E> linearCombination(Counter<E> c1, double w1, Counter<E> c2, double w2) {
    Counter<E> result = c1.getFactory().create();
    for (E o : c1.keySet()) {
      result.incrementCount(o, c1.getCount(o) * w1);
    }
    for (E o : c2.keySet()) {
      result.incrementCount(o, c2.getCount(o) * w2);
    }
    return result;
  }

  public static <T1, T2> double pointwiseMutualInformation(
      Counter<T1> var1Distribution,
      Counter<T2> var2Distribution,
      Counter<Pair<T1, T2>> jointDistribution,
      Pair<T1, T2> values) {
    double var1Prob = var1Distribution.getCount(values.first);
    double var2Prob = var2Distribution.getCount(values.second);
    double jointProb = jointDistribution.getCount(values);
    double pmi = Math.log(jointProb) - Math.log(var1Prob) - Math.log(var2Prob);
    return pmi / LOG_E_2;
  }

  @SuppressWarnings("unchecked")
  public static <E,C extends Counter<E>> C perturbCounts(C c, Random random, double p) {
    C result = (C)c.getFactory().create();
    for (E key : c.keySet()) {
      double count = c.getCount(key);
      double noise = -Math.log(1.0 - random.nextDouble()); // inverse of CDF for exponential distribution
      //      System.err.println("noise=" + noise);
      double perturbedCount = count + noise * p;
      result.setCount(key, perturbedCount);
    }
    return result;
  }

  /**
   * Great for debugging.
   *
   */
  public static <E> void printCounterComparison(Counter<E> a, Counter<E> b) {
    printCounterComparison(a, b, System.err);
  }

  /**
   * Great for debugging.
   *
   */
  public static <E> void printCounterComparison(Counter<E> a, Counter<E> b, PrintStream out) {
    if (a.equals(b)) {
      out.println("Counters are equal.");
      return;
    }
    for (E key : a.keySet()) {
      double aCount = a.getCount(key);
      double bCount = b.getCount(key);
      if (Math.abs(aCount - bCount) > 1e-5) {
        out.println("Counters differ on key " + key + '\t' + a.getCount(key) + " vs. " + b.getCount(key));
      }
    }
    // left overs
    Set<E> rest = new HashSet<E>(b.keySet());
    rest.removeAll(a.keySet());

    for (E key : rest) {
      double aCount = a.getCount(key);
      double bCount = b.getCount(key);
      if (Math.abs(aCount - bCount) > 1e-5) {
        out.println("Counters differ on key " + key + '\t' + a.getCount(key) + " vs. " + b.getCount(key));
      }
    }
  }

  public static <E> Counter<Double> getCountCounts(Counter<E> c) {
    Counter<Double> result = new ClassicCounter<Double>();
    for (double v : c.values()) {
      result.incrementCount(v);
    }
    return result;
  }

  /**
   * Returns a new Counter which is scaled by the given scale factor.
   *
   * @param c The counter to scale. It is not changed
   * @param s The constant to scale the counter by
   * @return A new Counter which is the argument scaled by the given scale factor.
   */
  @SuppressWarnings("unchecked")
  public static <E,C extends Counter<E>> C scale(C c, double s) {
    C scaled = (C)c.getFactory().create();
    for (E key : c.keySet()) {
      scaled.setCount(key, c.getCount(key) * s);
    }
    return scaled;
  }

  /**
   * Returns a new Counter which is the input counter with log tf scaling
   *
   * @param c The counter to scale. It is not changed
   * @param base The base of the logarithm used for tf scaling by 1 + log tf
   * @return A new Counter which is the argument scaled by the given scale factor.
   */
  @SuppressWarnings("unchecked")
  public static <E,C extends Counter<E>> C tfLogScale(C c, double base) {
    C scaled = (C) c.getFactory().create();
    for (E key : c.keySet()) {
      double cnt = c.getCount(key);
      double scaledCnt = 0.0;
      if (cnt > 0) {
        scaledCnt = 1.0 + SloppyMath.log(cnt, base);
      }
      scaled.setCount(key, scaledCnt);
    }
    return scaled;
  }


  public static <E extends Comparable<E>> void printCounterSortedByKeys(Counter<E> c) {
    List<E> keyList = new ArrayList<E>(c.keySet());
    Collections.sort(keyList);
    for (E o : keyList) {
      System.out.println(o + ":" + c.getCount(o));
    }
  }

  /**
   * Loads a Counter from a text file. File must have the format of one key/count pair per line,
   * separated by whitespace.
   *
   * @param filename the path to the file to load the Counter from
   * @param c        the Class to instantiate each member of the set. Must have a String constructor.
   * @return The counter loaded from the file.
   */
  public static <E> ClassicCounter<E> loadCounter(String filename, Class<E> c) throws RuntimeException {
    ClassicCounter<E> counter = new ClassicCounter<E>();
    loadIntoCounter(filename, c, counter);
    return counter;
  }

  /**
   * Loads a Counter from a text file. File must have the format of one key/count pair per line,
   * separated by whitespace.
   *
   * @param filename the path to the file to load the Counter from
   * @param c        the Class to instantiate each member of the set. Must have a String constructor.
   * @return The counter loaded from the file.
   */
  public static <E> IntCounter<E> loadIntCounter(String filename, Class<E> c) throws Exception {
    IntCounter<E> counter = new IntCounter<E>();
    loadIntoCounter(filename, c, counter);
    return counter;
  }

  /**
   * Loads a file into an GenericCounter.
   */
  private static <E> void loadIntoCounter(String filename, Class<E> c, Counter<E> counter) throws RuntimeException {
    try {
      Constructor<E> m = c.getConstructor(String.class);
      BufferedReader in = new BufferedReader(new FileReader(filename));
      String line = in.readLine();
      while (line != null && line.length() > 0) {
        int endPos = Math.max(line.lastIndexOf(' '), line.lastIndexOf('\t'));

        counter.setCount(
            m.newInstance(line.substring(0, endPos).trim()),
            Double.parseDouble(line.substring(endPos, line.length()).trim()));

        line = in.readLine();
      }
      in.close();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Saves a Counter as one key/count pair per line separated by white space
   * to the given OutputStream.  Does not close the stream.
   */
  public static <E> void saveCounter(Counter<E> c, OutputStream stream) {
    PrintStream out = new PrintStream(stream);
    for (E key : c.keySet()) {
      out.println(key + " " + c.getCount(key));
    }
  }

  /**
   * Saves a Counter to a text file. Counter written as one key/count pair per line,
   * separated by whitespace.
   */
  public static <E> void saveCounter(Counter<E> c, String filename) throws IOException {
    FileOutputStream fos = new FileOutputStream(filename);
    saveCounter(c, fos);
    fos.close();
  }

  public static<T1, T2> TwoDimensionalCounter<T1, T2> load2DCounter(String filename, Class<T1> t1, Class <T2> t2) throws RuntimeException {
    try {
      TwoDimensionalCounter<T1,T2> tdc = new TwoDimensionalCounter<T1, T2>();
      Constructor<T1> m1 = t1.getConstructor(String.class);
      Constructor<T2> m2 = t2.getConstructor(String.class);
      BufferedReader in = new BufferedReader(new FileReader(filename));
      String line = in.readLine();
      while (line != null && line.length() > 0) {
        //TODO: Was this variable supposed to do something, or should we just get rid of it?
        //int endPos = Math.max(line.lastIndexOf(' '), line.lastIndexOf('\t'));
        String[] tuple = line.trim().split("\t");
        String outer = tuple[0]; String inner = tuple[1]; String valStr = tuple[2];
        tdc.setCount(
            m1.newInstance(outer.trim()),
            m2.newInstance(inner.trim()),
            Double.parseDouble(valStr.trim()));

        line = in.readLine();
      }
      in.close();
      return tdc;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static<T1, T2> void save2DCounter(TwoDimensionalCounter<T1, T2> tdc, String filename) throws IOException {
    PrintWriter out = new PrintWriter(new FileWriter(filename));
    for (T1 outer : tdc.firstKeySet()) {
      for (T2 inner : tdc.secondKeySet()) {
        out.println(outer + "\t" + inner + '\t' +tdc.getCount(outer, inner));
      }
    }
    out.close();
  }

  public static <T> void serializeCounter(Counter<T> c, String filename) throws IOException {
      // serialize to  file
      ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(filename)));
      out.writeObject(c);
      out.close();
  }

  public static <T> ClassicCounter<T> deserializeCounter(String filename) throws Exception {
      // reconstitute
      ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(filename)));
      ClassicCounter<T> c = ErasureUtils.<ClassicCounter<T>>uncheckedCast(in.readObject());
      in.close();
      return c;
  }


  /**
   * Returns a string representation of a Counter, displaying the keys and
   * their counts in decreasing order of count. At most k keys are displayed.
   * 
   * Note that this method subsumes many of the other toString methods, e.g.:
   * 
   * toString(c, k) and toBiggestValuesFirstString(c, k)
   *   => toSortedString(c, k, "%s=%f", ", ", "[%s]")
   * 
   * toVerticalString(c, k)
   *   => toSortedString(c, k, "%2$g\t%1$s", "\n", "%s\n")
   * 
   * @param counter       A Counter.
   * @param k             The number of keys to include. Use Integer.MAX_VALUE to
   *                      include all keys.
   * @param itemFormat    The format string for key/count pairs, where the key is
   *                      first and the value is second. To display the value first,
   *                      use argument indices, e.g. "%2$f %1$s".
   * @param joiner        The string used between pairs of key/value strings.
   * @param wrapperFormat The format string for wrapping text around the joined items,
   *                      where the joined item string value is "%s".
   * @return              The top k values from the Counter, formatted as specified.        
   */
  public static <T> String toSortedString(
      Counter<T> counter, int k, String itemFormat, String joiner, String wrapperFormat) {
    PriorityQueue<T> queue = toPriorityQueue(counter);
    List<String> strings = new ArrayList<String>();
    for (int rank = 0; rank < k && !queue.isEmpty(); ++rank) {
      T key = queue.removeFirst();
      double value = counter.getCount(key);
      strings.add(String.format(itemFormat, key, value));
    }
    return String.format(wrapperFormat, StringUtils.join(strings, joiner));
  }
  
  /**
   * Returns a string representation of a Counter, displaying the keys and
   * their counts in decreasing order of count. At most k keys are displayed.
   * 
   * @param counter       A Counter.
   * @param k             The number of keys to include. Use Integer.MAX_VALUE to
   *                      include all keys.
   * @param itemFormat    The format string for key/count pairs, where the key is
   *                      first and the value is second. To display the value first,
   *                      use argument indices, e.g. "%2$f %1$s".
   * @param joiner        The string used between pairs of key/value strings.
   * @return              The top k values from the Counter, formatted as specified.        
   */
  public static <T> String toSortedString(
      Counter<T> counter, int k, String itemFormat, String joiner) {
    return toSortedString(counter, k, itemFormat, joiner, "%s");
  }

  /**
   * Returns a string representation of a Counter, where (key, value) pairs are
   * sorted by key, and formatted as specified.
   * 
   * @param counter       The Counter.
   * @param itemFormat    The format string for key/count pairs, where the key is
   *                      first and the value is second. To display the value first,
   *                      use argument indices, e.g. "%2$f %1$s".
   * @param joiner        The string used between pairs of key/value strings.
   * @param wrapperFormat The format string for wrapping text around the joined items,
   *                      where the joined item string value is "%s".
   * @return              The Counter, formatted as specified.
   */
  public static <T extends Comparable<T>> String toSortedByKeysString(
      Counter<T> counter, String itemFormat, String joiner, String wrapperFormat) {
    List<String> strings = new ArrayList<String>();
    for (T key: CollectionUtils.sorted(counter.keySet())) {
      strings.add(String.format(itemFormat, key, counter.getCount(key)));
    }
    return String.format(wrapperFormat, StringUtils.join(strings, joiner));
  }

  /**
   * Returns a string representation which includes no more than the
   * maxKeysToPrint elements with largest counts. If maxKeysToPrint is
   * non-positive, all elements are printed.
   *
   * @param counter The Counter
   * @param maxKeysToPrint Max keys to print
   * @return A partial string representation
   */
  public static <E> String toString(Counter<E> counter, int maxKeysToPrint) {
    return Counters.toPriorityQueue(counter).toString(maxKeysToPrint);
  }

  public static <E> String toString(Counter<E> counter, NumberFormat nf) {
    StringBuilder sb = new StringBuilder();
    sb.append('{');
    List<E> list = ErasureUtils.sortedIfPossible(counter.keySet());
    //*/
    for (Iterator<E> iter = list.iterator(); iter.hasNext();) {
      E key = iter.next();
      sb.append(key);
      sb.append('=');
      sb.append(nf.format(counter.getCount(key)));
      if (iter.hasNext()) {
        sb.append(", ");
      }
    }
    sb.append('}');
    return sb.toString();
  }

  /** Pretty print a Counter. This one has more flexibility in formatting,
   *  and doesn't sort the keys.
   */
  public static <E> String toString(Counter<E> counter, NumberFormat nf,
                         String preAppend, String postAppend,
                         String keyValSeparator, String itemSeparator) {
    StringBuilder sb = new StringBuilder();
    sb.append(preAppend);
    // List<E> list = new ArrayList<E>(map.keySet());
    //     try {
    //       Collections.sort(list); // see if it can be sorted
    //     } catch (Exception e) {
    //     }
    for (Iterator<E> iter = counter.keySet().iterator(); iter.hasNext(); ) {
      E key = iter.next();
      double d = counter.getCount(key);
      sb.append(key);
      sb.append(keyValSeparator);
      sb.append(nf.format(d));
      if (iter.hasNext()) {
        sb.append(itemSeparator);
      }
    }
    sb.append(postAppend);
    return sb.toString();
  }


  public static <E> String toBiggestValuesFirstString(Counter<E> c) {
    return toPriorityQueue(c).toString();
  }

  // TODO this method seems badly written. It should exploit topK printing of PriorityQueue
  public static <E> String toBiggestValuesFirstString(Counter<E> c, int k) {
    PriorityQueue<E> pq = toPriorityQueue(c);
    PriorityQueue<E> largestK = new BinaryHeapPriorityQueue<E>();
    //TODO: Is there any reason the original (commented out) line is better than the one replacing it?
//    while (largestK.size() < k && ((Iterator<E>)pq).hasNext()) {
    while (largestK.size() < k && !pq.isEmpty()) {
      double firstScore = pq.getPriority(pq.getFirst());
      E first = pq.removeFirst();
      largestK.changePriority(first, firstScore);
    }
    return largestK.toString();
  }

  public static <T> String toBiggestValuesFirstString(Counter<Integer> c, int k, Index<T> index) {
    PriorityQueue<Integer> pq = toPriorityQueue(c);
    PriorityQueue<T> largestK = new BinaryHeapPriorityQueue<T>();
//    while (largestK.size() < k && ((Iterator)pq).hasNext()) { //same as above
    while (largestK.size() < k && !pq.isEmpty()) {
      double firstScore = pq.getPriority(pq.getFirst());
      int first = pq.removeFirst();
      largestK.changePriority(index.get(first), firstScore);
    }
    return largestK.toString();
  }
  
  public static <E> String toVerticalString(Counter<E> c) {
    return toVerticalString(c, Integer.MAX_VALUE);
  }

  public static <E> String toVerticalString(Counter<E> c, int k) {
    return toVerticalString(c, k, "%g\t%s", false);
  }

  public static <E> String toVerticalString(Counter<E> c, String fmt) {
    return toVerticalString(c, Integer.MAX_VALUE, fmt, false);
  }

  public static <E> String toVerticalString(Counter<E> c, int k, String fmt) {
    return toVerticalString(c, k, fmt, false);
  }

  /**
   * Returns a <code>String</code> representation of the <code>k</code> keys
   * with the largest counts in the given {@link Counter}, using the given
   * format string.
   *
   * @param c a Counter
   * @param k how many keys to print
   * @param fmt a format string, such as "%.0f\t%s" (do not include final "%n")
   * @param swap whether the count should appear after the key
   */
  public static <E> String toVerticalString(Counter<E> c, int k, String fmt, boolean swap) {
    PriorityQueue<E> q = Counters.toPriorityQueue(c);
    List<E> sortedKeys = q.toSortedList();
    StringBuilder sb = new StringBuilder();
    int i = 0;
    for (Iterator<E> keyI = sortedKeys.iterator(); keyI.hasNext() && i < k; i++) {
      E key = keyI.next();
      double val = q.getPriority(key);
      if (swap) {
        sb.append(String.format(fmt, key, val));
      } else {
        sb.append(String.format(fmt, val, key));
      }
      if (keyI.hasNext()) {
        sb.append('\n');
      }
    }
    return sb.toString();
  }

  /**
   *
   * @return Returns the maximum element of c that is within the restriction Collection
   */
  public static <E> E restrictedArgMax(Counter<E> c, Collection<E> restriction) {
    E maxKey = null;
    double max = Double.NEGATIVE_INFINITY;
    for (E key : restriction) {
      double count = c.getCount(key);
      if (count > max) {
        max = count;
        maxKey = key;
      }
    }
    return maxKey;
  }

  public static <T> Counter<T> toCounter(double[] counts, Index<T> index) {
    if (index.size()<counts.length) throw new IllegalArgumentException("Index not large enough to name all the array elements!");
    Counter<T> c = new ClassicCounter<T>();
    for (int i=0; i<counts.length; i++) {
      if (counts[i]!=0.0) c.setCount(index.get(i), counts[i]);
    }
    return c;
  }

  /**
   * Turns the given map and index into a counter instance.  For each entry
   * in counts, its key is converted to a counter key via lookup in the given
   * index.
   */
  public static <E> Counter<E> toCounter(
      Map<Integer,? extends Number> counts, Index<E> index) {

    Counter<E> counter = new ClassicCounter<E>();
    for (Entry<Integer,? extends Number> entry : counts.entrySet()) {
      counter.setCount(index.get(entry.getKey()), entry.getValue().doubleValue());
    }
    return counter;
  }

  /**
   * Creates a new TwoDimensionalCounter where all the counts are scaled by d.
   * Internally, uses Counters.scale();
   *
   * @return The TwoDimensionalCounter
   */
  public static <T1, T2> TwoDimensionalCounter<T1, T2> scale(TwoDimensionalCounter<T1, T2> c, double d) {
    TwoDimensionalCounter<T1, T2> result = new TwoDimensionalCounter<T1, T2>(c.getOuterMapFactory(), c.getInnerMapFactory());
    for (T1 key : c.firstKeySet()) {
      ClassicCounter<T2> ctr = c.getCounter(key);
      result.setCounter(key, scale(ctr, d));
    }
    return result;
  }

  /**
   * Does not assumes c is normalized.
   * @return A sample from c
   */
  @SuppressWarnings("unchecked") // This is not good code, but what's there to be done? TODO
  public static <T> T sample(Counter<T> c, Random rand) {
    Iterable<T> objects;
    Set<T> keySet = c.keySet();
    objects = c.keySet();
    if (rand == null) {
      rand = new Random();
    } else { //TODO: Seems like there should be a way to directly check if T is comparable
      if (!keySet.isEmpty() && keySet.iterator().next() instanceof Comparable) {
        List l = new ArrayList<T>(keySet);
        Collections.sort(l);
        objects = l;
      }
    }
    double r = rand.nextDouble() * c.totalCount();
    double total = 0.0;

    for (T t : objects) { // arbitrary ordering
      total += c.getCount(t);
      if (total>=r) return t;
    }
    // only chance of reaching here is if c isn't properly normalized, or if double math makes total<1.0
    return c.keySet().iterator().next();
  }


  /**
   * Does not assumes c is normalized.
   * @return A sample from c
   */

  public static <T> T sample(Counter<T> c) {
    return sample(c, null);
  }

  /**
   * Returns a counter where each element corresponds to the normalized
   * count of the corresponding element in c raised to the given power.
   */
  public static <E> Counter<E> powNormalized(Counter<E> c, double temp) {
    Counter<E> d = c.getFactory().create();
    double total = c.totalCount();
    for (E e : c.keySet()) {
      d.setCount(e, Math.pow(c.getCount(e)/total, temp));
    }
    return d;
  }

  public static <T> Counter<T> pow(Counter<T> c, double temp) {
    Counter<T> d = c.getFactory().create();
    for (T t : c.keySet()) {
      d.setCount(t, Math.pow(c.getCount(t), temp));
    }
    return d;
  }

  public static <T> void powInPlace(Counter<T> c, double temp) {
    for (T t : c.keySet()) {
      c.setCount(t, Math.pow(c.getCount(t), temp));
    }
  }

  public static <T> Counter<T> exp(Counter<T> c) {
    Counter<T> d = c.getFactory().create();
    for (T t : c.keySet()) {
      d.setCount(t, Math.exp(c.getCount(t)));
    }
    return d;
  }

  public static <T> void expInPlace(Counter<T> c) {
    for (T t : c.keySet()) {
      c.setCount(t, Math.exp(c.getCount(t)));
    }
  }

  public static <T> Counter<T> diff(Counter<T> goldFeatures, Counter<T> guessedFeatures) {
    Counter<T> result = goldFeatures.getFactory().create();
    for (T key : Sets.union(goldFeatures.keySet(), guessedFeatures.keySet())) {
      result.setCount(key, goldFeatures.getCount(key) - guessedFeatures.getCount(key));
    }
    retainNonZeros(result);
    return result;
  }

  /**
   * Default equality comparison for two counters potentially backed
   * by alternative implementations.
   */
  public static <E> boolean equals(Counter<E> o1, Counter<E> o2) {
    if (o1 == o2) { return true; }

    if (o1.totalCount() != o2.totalCount()) { return false;  }

    if (!o1.keySet().equals(o2.keySet())) { return false; }

    for (E key : o1.keySet()) {
      if (o1.getCount(key) != o2.getCount(key)) { return false; }
    }

    return true;
  }

  /**
   * Returns unmodifiable view of the counter.  changes to the underlying Counter
   * are written through to this Counter.
   * @param counter The counter
   * @return unmodifiable view of the counter
   */
  public static <T> Counter<T> unmodifiableCounter(final Counter<T> counter) {

    return new AbstractCounter<T>() {

      public void clear() {
        throw new UnsupportedOperationException();
      }

      public boolean containsKey(T key) {
        return counter.containsKey(key);
      }

      public double getCount(T key) {
        return counter.getCount(key);
      }

      public Factory<Counter<T>> getFactory() {
        return counter.getFactory();
      }

      public double remove(T key) {
        throw new UnsupportedOperationException();
      }

      public void setCount(T key, double value) {
        throw new UnsupportedOperationException();
      }

      @Override
      public double incrementCount(T key, double value) {
        throw new UnsupportedOperationException();
      }

      @Override
      public double incrementCount(T key) {
        throw new UnsupportedOperationException();
      }

      @Override
      public double logIncrementCount(T key, double value) {
        throw new UnsupportedOperationException();
      }

      public int size() {
        return counter.size();
      }

      public double totalCount() {
        return counter.totalCount();
      }

      public Collection<Double> values() {
        return counter.values();
      }

      public Set<T> keySet() {
        return Collections.unmodifiableSet(counter.keySet());
      }

      public Set<Entry<T, Double>> entrySet() {
        return Collections.unmodifiableSet(new AbstractSet<Entry<T,Double>>() {
          @Override
          public Iterator<Entry<T, Double>> iterator() {
            return new Iterator<Entry<T, Double>>() {
              final Iterator<Entry<T, Double>> inner = counter.entrySet().iterator();

              public boolean hasNext() {
                return inner.hasNext();
              }

              public Entry<T, Double> next() {
                return new Entry<T,Double>() {
                  final Entry<T,Double> e = inner.next();

                  public T getKey() {
                    return e.getKey();
                  }

                  @SuppressWarnings({"UnnecessaryBoxing", "UnnecessaryUnboxing"})
                  public Double getValue() {
                    return Double.valueOf(e.getValue().doubleValue());
                  }

                  public Double setValue(Double value) {
                    throw new UnsupportedOperationException();
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
            return counter.size();
          }
        });
      }

      public void setDefaultReturnValue(double rv) {
        throw new UnsupportedOperationException();
      }

      public double defaultReturnValue() {
        return counter.defaultReturnValue();
      }

    };
  }

  /**
   * Returns a counter whose keys are the elements in this priority queue, and
   * whose counts are the priorities in this queue.  In the event there are
   * multiple instances of the same element in the queue, the counter's count
   * will be the sum of the instances' priorities.
   *
   */
  public static <E> Counter<E> asCounter(FixedPrioritiesPriorityQueue<E> p) {
    FixedPrioritiesPriorityQueue<E> pq = p.clone();
    ClassicCounter<E> counter = new ClassicCounter<E>();
    while (pq.hasNext()) {
      double priority = pq.getPriority();
      E element = pq.next();
      counter.incrementCount(element, priority);
    }
    return counter;
  }

  /**
   * Returns a counter view of the given map.  Infers the numeric type
   * of the values from the first element in map.values().
   */
  @SuppressWarnings("unchecked")
  public static <E, N extends Number> Counter<E> fromMap(final Map<E,N> map) {
    if (map.isEmpty()) {
      throw new IllegalArgumentException("Map must have at least one element" +
      		" to infer numeric type; add an element first or use e.g." +
      		" fromMap(map, Integer.class)");
    }
    return fromMap(map, (Class)map.values().iterator().next().getClass());
  }

  /**
   * Returns a counter view of the given map.  The type parameter is the
   * type of the values in the map, which because of Java's generics type
   * erasure, can't be discovered by reflection if the map is currently empty.
   */
  public static <E, N extends Number> Counter<E> fromMap(final Map<E,N> map, final Class<N> type) {
    // get our initial total
    double initialTotal = 0;
    for (Entry<E,N> entry : map.entrySet()) {
      initialTotal += entry.getValue().doubleValue();
    }

    // and pass it in to the returned inner class with a final variable
    final double initialTotalFinal = initialTotal;

    return new AbstractCounter<E>() {
      double total = initialTotalFinal;
      double defRV = 0.0;

      public void clear() {
        map.clear();
      }

      public boolean containsKey(E key) {
        return map.containsKey(key);
      }

      public void setDefaultReturnValue(double rv) {
        defRV = rv;
      }

      public double defaultReturnValue() {
        return defRV;
      }

      @Override
      @SuppressWarnings("unchecked")
      public boolean equals(Object o) {
        if (this == o) {
          return true;
        } else if (! (o instanceof Counter)) {
          return false;
        } else {
          return Counters.equals(this, (Counter<E>) o);
        }
      }

      @Override
      public int hashCode() {
        return map.hashCode();
      }

      public Set<Entry<E, Double>> entrySet() {
        return new AbstractSet<Entry<E,Double>>() {
          Set<Entry<E,N>> entries = map.entrySet();

          @Override
          public Iterator<Entry<E, Double>> iterator() {
            return new Iterator<Entry<E,Double>>() {
              Iterator<Entry<E,N>> it = entries.iterator();
              Entry<E,N> lastEntry = null;

              public boolean hasNext() {
                return it.hasNext();
              }

              public Entry<E, Double> next() {
                final Entry<E,N> entry = it.next();
                lastEntry = entry;

                return new Entry<E,Double>() {
                  public E getKey() {
                    return entry.getKey();
                  }

                  public Double getValue() {
                    return entry.getValue().doubleValue();
                  }

                  public Double setValue(Double value) {
                    final double lastValue = entry.getValue().doubleValue();
                    double rv;

                    if (type == Double.class) {
                      rv = ErasureUtils.<Entry<E,Double>>uncheckedCast(entry).setValue(value);
                    } else if (type == Integer.class) {
                      rv = ErasureUtils.<Entry<E,Integer>>uncheckedCast(entry).setValue(value.intValue());
                    } else if (type == Float.class) {
                      rv = ErasureUtils.<Entry<E,Float>>uncheckedCast(entry).setValue(value.floatValue());
                    } else if (type == Long.class) {
                      rv = ErasureUtils.<Entry<E,Long>>uncheckedCast(entry).setValue(value.longValue());
                    } else if (type == Short.class) {
                      rv = ErasureUtils.<Entry<E,Short>>uncheckedCast(entry).setValue(value.shortValue());
                    } else {
                      throw new RuntimeException("Unrecognized numeric type in wrapped counter");
                    }

                    // need to call getValue().doubleValue() to make sure
                    // we keep the same precision as the underlying map
                    total += entry.getValue().doubleValue() - lastValue;

                    return rv;
                  }
                };
              }

              public void remove() {
                total -= lastEntry.getValue().doubleValue();
                it.remove();
              }
            };
          }

          @Override
          public int size() {
            return map.size();
          }
        };
      }

      public double getCount(E key) {
        final Number value = map.get(key);
        return value != null ? value.doubleValue() : defRV;
      }

      public Factory<Counter<E>> getFactory() {
        return new Factory<Counter<E>>() {

          private static final long serialVersionUID = -4063129407369590522L;

          public Counter<E> create() {
            // return a HashMap backed by the same numeric type to
            // keep the precion of the returned counter consistent with
            // this one's precision
            return fromMap(new HashMap<E,N>(), type);
          }
        };
      }

      public Set<E> keySet() {
        return new AbstractSet<E>() {
          @Override
          public Iterator<E> iterator() {
            return new Iterator<E>() {
              Iterator<E> it = map.keySet().iterator();

              public boolean hasNext() {
                return it.hasNext();
              }

              public E next() {
                return it.next();
              }

              public void remove() {
                throw new UnsupportedOperationException("Cannot remove from key set");
              }
            };
          }

          @Override
          public int size() {
            return map.size();
          }
        };
      }

      public double remove(E key) {
        final Number removed = map.remove(key);
        if (removed != null) {
          final double rv = removed.doubleValue();
          total -= rv;
          return rv;
        }
        return defRV;
      }

      public void setCount(E key, double value) {
        final Double lastValue;
        double newValue;

        if (type == Double.class) {
          lastValue = ErasureUtils.<Map<E,Double>>uncheckedCast(map).put(key, value);
          newValue = value;
        } else if (type == Integer.class) {
          final Integer last = ErasureUtils.<Map<E,Integer>>uncheckedCast(map).put(key, (int)value);
          lastValue = last != null ? last.doubleValue() : null;
          newValue = ((int)value);
        } else if (type == Float.class) {
          final Float last = ErasureUtils.<Map<E,Float>>uncheckedCast(map).put(key, (float)value);
          lastValue = last != null ? last.doubleValue() : null;
          newValue = ((float)value);
        } else if (type == Long.class) {
          final Long last = ErasureUtils.<Map<E,Long>>uncheckedCast(map).put(key, (long)value);
          lastValue = last != null ? last.doubleValue() : null;
          newValue = ((long)value);
        } else if (type == Short.class) {
          final Short last = ErasureUtils.<Map<E,Short>>uncheckedCast(map).put(key, (short)value);
          lastValue = last != null ? last.doubleValue() : null;
          newValue = ((short)value);
        } else {
          throw new RuntimeException("Unrecognized numeric type in wrapped counter");
        }

        // need to use newValue instead of value to make sure we
        // keep same precision as underlying map.
        total += newValue - (lastValue != null ? lastValue : 0);
      }

      public int size() {
        return map.size();
      }

      public double totalCount() {
        return total;
      }

      public Collection<Double> values() {
        return new AbstractCollection<Double>() {
          @Override
          public Iterator<Double> iterator() {
            return new Iterator<Double>() {
              final Iterator<N> it = map.values().iterator();

              public boolean hasNext() {
                return it.hasNext();
              }

              public Double next() {
                return it.next().doubleValue();
              }

              public void remove() {
                throw new UnsupportedOperationException("Cannot remove from values collection");
              }
            };
          }

          @Override
          public int size() {
            return map.size();
          }
        };
      }
    };
  }

  /**
   * Returns a map view of the given counter.
   */
  public static <E> Map<E,Double> asMap(final Counter<E> counter) {
    return new AbstractMap<E,Double>() {
      @Override
      public int size() {
        return counter.size();
      }

      @Override
      public Set<Entry<E, Double>> entrySet() {
        return counter.entrySet();
      }

      @Override
      @SuppressWarnings("unchecked")
      public boolean containsKey(Object key) {
        return counter.containsKey((E)key);
      }

      @Override
      @SuppressWarnings("unchecked")
      public Double get(Object key) {
        return counter.getCount((E)key);
      }

      @Override
      public Double put(E key, Double value) {
        double last = counter.getCount(key);
        counter.setCount(key, value);
        return last;
      }

      @Override
      @SuppressWarnings("unchecked")
      public Double remove(Object key) {
        return counter.remove((E)key);
      }

      @Override
      public Set<E> keySet() {
        return counter.keySet();
      }
    };
  }

  /**
   * Default comparator for breaking ties in argmin and argmax.
  //TODO: What type should this be?
  // Unused, so who cares?
  private static final Comparator<Object> hashCodeComparator = new Comparator<Object>() {
      public int compare(Object o1, Object o2) {
        return o1.hashCode() - o2.hashCode();
      }

      public boolean equals(Comparator comparator) {
        return (comparator == this);
      }
  };
   */

  /**
   * Comparator that uses natural ordering.
   * Returns 0 if o1 is not Comparable.
   */
  static class NaturalComparator<E> implements Comparator<E> {
    public NaturalComparator() {}
    @Override
    public String toString() { return "NaturalComparator"; }
    @SuppressWarnings("unchecked")
    public int compare(E o1, E o2) {
      if (o1 instanceof Comparable) {
        return (((Comparable<E>) o1).compareTo(o2));
      }
      return 0; // soft-fail
    }
  }



}
