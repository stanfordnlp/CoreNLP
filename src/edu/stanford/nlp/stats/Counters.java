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

package edu.stanford.nlp.stats;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.math.SloppyMath;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;
import edu.stanford.nlp.util.logging.PrettyLogger;
import edu.stanford.nlp.util.logging.Redwood.RedwoodChannels;

/**
 * Static methods for operating on a {@link Counter}.
 *
 * All methods that change their arguments change the <i>first</i> argument
 * (only), and have "InPlace" in their name. This class also provides access to
 * Comparators that can be used to sort the keys or entries of this Counter by
 * the counts, in either ascending or descending order.
 *
 * @author Galen Andrew (galand@cs.stanford.edu)
 * @author Jeff Michels (jmichels@stanford.edu)
 * @author dramage
 * @author daniel cer (http://dmcer.net)
 * @author Christopher Manning
 * @author stefank (Optimized dot product)
 */
public class Counters  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(Counters.class);

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

  /**
   * Transform log space values into a probability distribution in place. On the
   * assumption that the values in the Counter are in log space, this method
   * calculates their sum, and then subtracts the log of their sum from each
   * element. That is, if a counter has keys c1, c2, c3 with values v1, v2, v3,
   * the value of c1 becomes v1 - log(e^v1 + e^v2 + e^v3). After this, e^v1 +
   * e^v2 + e^v3 = 1.0, so Counters.logSum(c) = 0.0 (approximately).
   *
   * @param c The Counter to log normalize in place
   */
  @SuppressWarnings( { "UnnecessaryUnboxing" })
  public static <E> void logNormalizeInPlace(Counter<E> c) {
    double logsum = logSum(c);
    // for (E key : c.keySet()) {
    // c.incrementCount(key, -logsum);
    // }
    // This should be faster
    for (Map.Entry<E, Double> e : c.entrySet()) {
      e.setValue(e.getValue().doubleValue() - logsum);
    }
  }

  //
  // Query operations
  //

  /**
   * Returns the value of the maximum entry in this counter. This is also the
   * L_infinity norm. An empty counter is given a max value of
   * Double.NEGATIVE_INFINITY.
   *
   * @param c The Counter to find the max of
   * @return The maximum value of the Counter
   */
  public static <E> double max(Counter<E> c) {
    return max(c, Double.NEGATIVE_INFINITY);  // note[gabor]: Should the default actually be 0 rather than negative_infinity?
  }

  /**
   * Returns the value of the maximum entry in this counter. This is also the
   * L_infinity norm. An empty counter is given a max value of
   * Double.NEGATIVE_INFINITY.
   *
   * @param c The Counter to find the max of
   * @param valueIfEmpty The value to return if this counter is empty (i.e., the maximum is not well defined.
   * @return The maximum value of the Counter
   */
  public static <E> double max(Counter<E> c, double valueIfEmpty) {
    if (c.size() == 0) {
      return valueIfEmpty;
    } else {
      double max = Double.NEGATIVE_INFINITY;
      for (double v : c.values()) {
        max = Math.max(max, v);
      }
      return max;
    }
  }

  /**
   * Takes in a Collection of something and makes a counter, incrementing once
   * for each object in the collection.
   *
   * @param c The Collection to turn into a counter
   * @return The counter made out of the collection
   */
  public static <E> Counter<E> asCounter(Collection<E> c) {
    Counter<E> count = new ClassicCounter<>();
    for (E elem : c) {
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
   * Finds and returns the key in the Counter with the largest count. Returning
   * null if count is empty.
   *
   * @param c The Counter
   * @return The key in the Counter with the largest count.
   */
  public static <E> E argmax(Counter<E> c) {
    return argmax(c, (x, y) -> 0, null);

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
      if (argmin == null || count < min) { // || (count == min && tieBreaker.compare(key, argmin) < 0)
        min = count;
        argmin = key;
      }
    }
    return argmin;
  }

  /**
   * Finds and returns the key in the Counter with the largest count. Returning
   * null if count is empty.
   *
   * @param c The Counter
   * @param tieBreaker the tie breaker for when elements have the same value.
   * @return The key in the Counter with the largest count.
   */
  public static <E> E argmax(Counter<E> c, Comparator<E> tieBreaker) {
    return argmax(c, tieBreaker, (E) null);
  }

  /**
   * Finds and returns the key in the Counter with the largest count. Returning
   * null if count is empty.
   *
   * @param c The Counter
   * @param tieBreaker the tie breaker for when elements have the same value.
   * @param defaultIfEmpty The value to return if the counter is empty.
   * @return The key in the Counter with the largest count.
   */
  public static <E> E argmax(Counter<E> c, Comparator<E> tieBreaker, E defaultIfEmpty) {
    if (Thread.interrupted()) {  // A good place to check for interrupts -- called from many annotators
      throw new RuntimeInterruptedException();
    }
    if (c.size() == 0) {
      return defaultIfEmpty;
    }
    double max = Double.NEGATIVE_INFINITY;
    E argmax = null;
    for (E key : c.keySet()) {
      double count = c.getCount(key);
      if (argmax == null || count > max || (count == max && tieBreaker.compare(key, argmax) < 0)) {
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
  public static <E> E argmin(Counter<E> c, Comparator<E> tieBreaker) {
    double min = Double.POSITIVE_INFINITY;
    E argmin = null;

    for (E key : c.keySet()) {
      double count = c.getCount(key);
      if (argmin == null || count < min || (count == min && tieBreaker.compare(key, argmin) < 0)) {
        min = count;
        argmin = key;
      }
    }
    return argmin;
  }

  /**
   * Returns the mean of all the counts (totalCount/size).
   *
   * @param c The Counter to find the mean of.
   * @return The mean of all the counts (totalCount/size).
   */
  public static <E> double mean(Counter<E> c) {
    return c.totalCount() / c.size();
  }

  public static <E> double standardDeviation(Counter<E> c) {
    double std = 0;
    double mean = c.totalCount() / c.size();
    for (Map.Entry<E, Double> en : c.entrySet()) {
      std += (en.getValue() - mean) * (en.getValue() - mean);
    }
    return Math.sqrt(std / c.size());
  }

  //
  // In-place arithmetic
  //

  /**
   * Sets each value of target to be target[k]+scale*arg[k] for all keys k in
   * target.
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
   * Sets each value of target to be target[k]+arg[k] for all keys k in arg.
   */
  public static <E> void addInPlace(Counter<E> target, Counter<E> arg) {
    for (Map.Entry<E, Double> entry : arg.entrySet()) {
      double count = entry.getValue();
      if (count != 0) {
        target.incrementCount(entry.getKey(), count);
      }
    }
  }

  /**
   * Sets each value of double[] target to be
   * target[idx.indexOf(k)]+a.getCount(k) for all keys k in arg
   */
  public static <E> void addInPlace(double[] target, Counter<E> arg, Index<E> idx) {
    for (Map.Entry<E, Double> entry : arg.entrySet()) {
      target[idx.indexOf(entry.getKey())] += entry.getValue();
    }
  }

  /**
   * For all keys (u,v) in arg1 and arg2, sets return[u,v] to be summation of both.
   * @param <T1>
   * @param <T2>
   */
  public static <T1, T2> TwoDimensionalCounter<T1, T2> add(TwoDimensionalCounter<T1, T2> arg1, TwoDimensionalCounter<T1, T2> arg2) {
    TwoDimensionalCounter<T1, T2> add = new TwoDimensionalCounter<>();
    Counters.addInPlace(add , arg1);
    Counters.addInPlace(add , arg2);
    return add;
  }

   /**
   * For all keys (u,v) in arg, sets target[u,v] to be target[u,v] + scale *
   * arg[u,v].
   *
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
   * For all keys (u,v) in arg, sets target[u,v] to be target[u,v] + arg[u,v].
   *
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
   * Sets each value of target to be target[k]+
   * value*(num-of-times-it-occurs-in-collection) if the key is present in the arg
   * collection.
   */
  public static <E> void addInPlace(Counter<E> target, Collection<E> arg, double value) {
    for (E key : arg) {
      target.incrementCount(key, value);
    }
  }

  /**
   * For all keys (u,v) in target, sets target[u,v] to be target[u,v] + value
   *
   * @param <T1>
   * @param <T2>
   */
  public static <T1, T2> void addInPlace(TwoDimensionalCounter<T1, T2> target, double value) {
    for (T1 outer : target.firstKeySet()){
        addInPlace(target.getCounter(outer), value);
      }
  }

  /**
   * Sets each value of target to be target[k]+
   * num-of-times-it-occurs-in-collection if the key is present in the arg
   * collection.
   */
  public static <E> void addInPlace(Counter<E> target, Collection<E> arg) {
    for (E key : arg) {
      target.incrementCount(key, 1);
    }
  }

  /**
   * Increments all keys in a Counter by a specific value.
   */
  public static <E> void addInPlace(Counter<E> target, double value) {
    for (E key : target.keySet()) {
      target.incrementCount(key, value);
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
   * Sets each value of double[] target to be
   * target[idx.indexOf(k)]-a.getCount(k) for all keys k in arg
   */
  public static <E> void subtractInPlace(double[] target, Counter<E> arg, Index<E> idx) {
    for (Map.Entry<E, Double> entry : arg.entrySet()) {
      target[idx.indexOf(entry.getKey())] -= entry.getValue();
    }
  }

  /**
   * Divides every non-zero count in target by the corresponding value in the
   * denominator Counter. Beware that this can give NaN values for zero counts
   * in the denominator counter!
   */
  public static <E> void divideInPlace(Counter<E> target, Counter<E> denominator) {
    for (E key : target.keySet()) {
      target.setCount(key, target.getCount(key) / denominator.getCount(key));
    }
  }

  /**
   * Multiplies every count in target by the corresponding value in the term
   * Counter.
   */
  public static <E> void dotProductInPlace(Counter<E> target, Counter<E> term) {
    for (E key : target.keySet()) {
      target.setCount(key, target.getCount(key) * term.getCount(key));
    }
  }

  /**
   * Divides each value in target by the given divisor, in place.
   *
   * @param target The values in this Counter will be changed throughout by the
   *          multiplier
   * @param divisor The number by which to change each number in the Counter
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
   * @param target The values in this Counter will be multiplied by the
   *          multiplier
   * @param multiplier The number by which to change each number in the Counter
   */
  public static <E> Counter<E> multiplyInPlace(Counter<E> target, double multiplier) {
    for (Entry<E, Double> entry : target.entrySet()) {
      target.setCount(entry.getKey(), entry.getValue() * multiplier);
    }
    return target;
  }

  /**
   * Multiplies each value in target by the count of the key in mult, in place. Returns non zero entries
   *
   * @param target The counter
   * @param mult The counter you want to multiply with target
   */
  public static <E> Counter<E> multiplyInPlace(Counter<E> target, Counter<E> mult) {
    for (Entry<E, Double> entry : target.entrySet()) {
      target.setCount(entry.getKey(), entry.getValue() * mult.getCount(entry.getKey()));
    }
    Counters.retainNonZeros(target);
    return target;
  }

  /**
   * Normalizes the target counter in-place, so the sum of the resulting values
   * equals 1.
   *
   * @param <E> Type of elements in Counter
   */
  public static <E> void normalize(Counter<E> target) {
    divideInPlace(target, target.totalCount());
  }

  /**
   * L1 normalize a counter. Return a counter that is a probability distribution,
   * so the sum of the resulting value equals 1.
   *
   * @param c The {@link Counter} to be L1 normalized. This counter is not
   *          modified.
   * @return A new L1-normalized Counter based on c.
   */
  public static <E, C extends Counter<E>> C asNormalizedCounter(C c) {
    return scale(c, 1.0 / c.totalCount());
  }

  /**
   * Normalizes the target counter in-place, so the sum of the resulting values
   * equals 1.
   *
   * @param <E> Type of elements in TwoDimensionalCounter
   * @param <F> Type of elements in TwoDimensionalCounter
   */
  public static <E, F> void normalize(TwoDimensionalCounter<E, F> target) {
    Counters.divideInPlace(target, target.totalCount());
  }

  public static <E> void logInPlace(Counter<E> target) {
    for (E key : target.keySet()) {
      target.setCount(key, Math.log(target.getCount(key)));
    }
  }

  //
  // Selection Operators
  //

  /**
   * Delete 'top' and 'bottom' number of elements from the top and bottom
   * respectively
   */
  public static <E> List<E> deleteOutofRange(Counter<E> c, int top, int bottom) {

    List<E> purgedItems = new ArrayList<>();
    int numToPurge = top + bottom;
    if (numToPurge <= 0) {
      return purgedItems;
    }

    List<E> l = Counters.toSortedList(c);
    for (int i = 0; i < top; i++) {
      E item = l.get(i);
      purgedItems.add(item);
      c.remove(item);
    }
    int size = c.size();
    for (int i = c.size() - 1; i >= (size - bottom); i--) {
      E item = l.get(i);
      purgedItems.add(item);
      c.remove(item);
    }
    return purgedItems;
  }

  /**
   * Removes all entries from c except for the top {@code num}.
   */
  public static <E> void retainTop(Counter<E> c, int num) {
    int numToPurge = c.size() - num;
    if (numToPurge <= 0) {
      return;
    }

    List<E> l = Counters.toSortedList(c, true);
    for (int i = 0; i < numToPurge; i++) {
      c.remove(l.get(i));
    }
  }

  /**
   * Removes all entries from c except for the top {@code num}.
   */
  public static <E extends Comparable<E>> void retainTopKeyComparable(Counter<E> c, int num) {
    int numToPurge = c.size() - num;
    if (numToPurge <= 0) {
      return;
    }

    List<E> l = Counters.toSortedListKeyComparable(c);
    Collections.reverse(l);
    for (int i = 0; i < numToPurge; i++) {
      c.remove(l.get(i));
    }
  }

  /**
   * Removes all entries from c except for the bottom {@code num}.
   */
  public static <E> List<E> retainBottom(Counter<E> c, int num) {
    int numToPurge = c.size() - num;
    if (numToPurge <= 0) {
      return Generics.newArrayList();
    }

    List<E> removed = new ArrayList<>();
    List<E> l = Counters.toSortedList(c);
    for (int i = 0; i < numToPurge; i++) {
      E rem = l.get(i);
      removed.add(rem);
      c.remove(rem);
    }
    return removed;
  }

  /**
   * Removes all entries with 0 count in the counter, returning the set of
   * removed entries.
   */
  public static <E> Set<E> retainNonZeros(Counter<E> counter) {
    Set<E> removed = Generics.newHashSet();
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
   * @param counter The counter.
   * @param countThreshold
   *          The minimum count for an entry to be kept. Entries (strictly) less
   *          than this threshold are discarded.
   * @return The set of discarded entries.
   */
  public static <E> Set<E> retainAbove(Counter<E> counter, double countThreshold) {
    Set<E> removed = Generics.newHashSet();
    for (E key : counter.keySet()) {
      if (counter.getCount(key) < countThreshold) {
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
   * @param counter The counter.
   * @param countThreshold
   *          The minimum count for an entry to be kept. Entries (strictly) less
   *          than this threshold are discarded.
   * @return The set of discarded entries.
   */
  public static <E1, E2> Set<Pair<E1, E2>> retainAbove(
      TwoDimensionalCounter<E1, E2> counter, double countThreshold) {

    Set<Pair<E1, E2>> removed = new HashSet<>();
    for (Entry<E1, ClassicCounter<E2>> en : counter.entrySet()) {
      for (Entry<E2, Double> en2 : en.getValue().entrySet()) {
        if (counter.getCount(en.getKey(), en2.getKey()) < countThreshold) {
          removed.add(new Pair<>(en.getKey(), en2.getKey()));
        }
      }
    }
    for (Pair<E1, E2> key : removed) {
      counter.remove(key.first(), key.second());
    }
    return removed;
  }

  /**
   * Removes all entries with counts above the given threshold, returning the
   * set of removed entries.
   *
   * @param counter The counter.
   * @param countMaxThreshold
   *          The maximum count for an entry to be kept. Entries (strictly) more
   *          than this threshold are discarded.
   * @return The set of discarded entries.
   */
  public static <E> Counter<E> retainBelow(Counter<E> counter, double countMaxThreshold) {
    Counter<E> removed = new ClassicCounter<>();
    for (E key : counter.keySet()) {
      double count = counter.getCount(key);
      if (counter.getCount(key) > countMaxThreshold) {
        removed.setCount(key, count);
      }
    }
    for (Entry<E, Double> key : removed.entrySet()) {
      counter.remove(key.getKey());
    }
    return removed;
  }

  /**
   * Removes all entries with keys that does not match one of the given patterns.
   *
   * @param counter The counter.
   * @param matchPatterns pattern for key to match
   * @return The set of discarded entries.
   */
  public static Set<String> retainMatchingKeys(Counter<String> counter, List<Pattern> matchPatterns) {
    Set<String> removed = Generics.newHashSet();
    for (String key : counter.keySet()) {
      boolean matched = false;
      for (Pattern pattern : matchPatterns) {
        if (pattern.matcher(key).matches()) {
          matched = true;
          break;
        }
      }
      if (!matched) {
        removed.add(key);
      }
    }
    for (String key : removed) {
      counter.remove(key);
    }
    return removed;
  }

  /**
   * Removes all entries with keys that does not match the given set of keys.
   *
   * @param counter The counter
   * @param matchKeys Keys to match
   * @return The set of discarded entries.
   */
  public static<E> Set<E> retainKeys(Counter<E> counter, Collection<E> matchKeys) {
    Set<E> removed = Generics.newHashSet();
    for (E key : counter.keySet()) {
      boolean matched = matchKeys.contains(key);
      if (!matched) {
        removed.add(key);
      }
    }
    for (E key : removed) {
      counter.remove(key);
    }
    return removed;
  }

  /**
   * Removes all entries with keys in the given collection
   *
   * @param <E>
   * @param counter
   * @param removeKeysCollection
   */
  public static <E> void removeKeys(Counter<E> counter, Collection<E> removeKeysCollection) {

    for (E key : removeKeysCollection)
      counter.remove(key);
  }

  /**
   * Removes all entries with keys (first key set) in the given collection
   *
   * @param <E>
   * @param counter
   * @param removeKeysCollection
   */
  public static <E, F> void removeKeys(TwoDimensionalCounter<E, F> counter, Collection<E> removeKeysCollection) {

    for (E key : removeKeysCollection)
      counter.remove(key);
  }

  /**
   * Returns the set of keys whose counts are at or above the given threshold.
   * This set may have 0 elements but will not be null.
   *
   * @param c The Counter to examine
   * @param countThreshold
   *          Items equal to or above this number are kept
   * @return A (non-null) Set of keys whose counts are at or above the given
   *         threshold.
   */
  public static <E> Set<E> keysAbove(Counter<E> c, double countThreshold) {
    Set<E> keys = Generics.newHashSet();
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
    Set<E> keys = Generics.newHashSet();
    for (E key : c.keySet()) {
      if (c.getCount(key) <= countThreshold) {
        keys.add(key);
      }
    }
    return (keys);
  }

  /**
   * Returns the set of keys that have exactly the given count. This set may
   * have 0 elements but will not be null.
   */
  public static <E> Set<E> keysAt(Counter<E> c, double count) {
    Set<E> keys = Generics.newHashSet();
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
   * Returns the counter with keys modified according to function F. Eager
   * evaluation. If two keys are same after the transformation, one of the values is randomly chosen (depending on how the keyset is traversed)
   */
  public static <T1, T2> Counter<T2> transform(Counter<T1> c, Function<T1, T2> f) {
    Counter<T2> c2 = new ClassicCounter<>();
    for (T1 key : c.keySet()) {
      c2.setCount(f.apply(key), c.getCount(key));
    }
    return c2;
  }

  /**
   * Returns the counter with keys modified according to function F. If two keys are same after the transformation, their values get added up.
   */
  public static <T1, T2> Counter<T2> transformWithValuesAdd(Counter<T1> c, Function<T1, T2> f) {
    Counter<T2> c2 = new ClassicCounter<>();
    for (T1 key : c.keySet()) {
      c2.incrementCount(f.apply(key), c.getCount(key));
    }
    return c2;
  }

  //
  // Conversion to other types
  //

  /**
   * Returns a comparator backed by this counter: two objects are compared by
   * their associated values stored in the counter. This comparator returns keys
   * by ascending numeric value. Note that this ordering is not fixed, but
   * depends on the mutable values stored in the Counter. Doing this comparison
   * does not depend on the type of the key, since it uses the numeric value,
   * which is always Comparable.
   *
   * @param counter The Counter whose values are used for ordering the keys
   * @return A Comparator using this ordering
   */
  public static <E> Comparator<E> toComparator(final Counter<E> counter) {
    return (o1, o2) -> Double.compare(counter.getCount(o1), counter.getCount(o2));
  }

  /**
   * Returns a comparator backed by this counter: two objects are compared by
   * their associated values stored in the counter. This comparator returns keys
   * by ascending numeric value. Note that this ordering is not fixed, but
   * depends on the mutable values stored in the Counter. Doing this comparison
   * does not depend on the type of the key, since it uses the numeric value,
   * which is always Comparable.
   *
   * @param counter The Counter whose values are used for ordering the keys
   * @return A Comparator using this ordering
   */
  public static <E extends Comparable<E>> Comparator<E> toComparatorWithKeys(final Counter<E> counter) {
    return (o1, o2) -> {
      int res = Double.compare(counter.getCount(o1), counter.getCount(o2));
      if (res == 0) {
        return o1.compareTo(o2);
      } else {
        return res;
      }
    };
  }

  /**
   * Returns a comparator backed by this counter: two objects are compared by
   * their associated values stored in the counter. This comparator returns keys
   * by descending numeric value. Note that this ordering is not fixed, but
   * depends on the mutable values stored in the Counter. Doing this comparison
   * does not depend on the type of the key, since it uses the numeric value,
   * which is always Comparable.
   *
   * @param counter The Counter whose values are used for ordering the keys
   * @return A Comparator using this ordering
   */
  public static <E> Comparator<E> toComparatorDescending(final Counter<E> counter) {
    return (o1, o2) -> Double.compare(counter.getCount(o2), counter.getCount(o1));
  }

  /**
   * Returns a comparator suitable for sorting this Counter's keys or entries by
   * their respective value or magnitude (by absolute value). If
   * <tt>ascending</tt> is true, smaller magnitudes will be returned first,
   * otherwise higher magnitudes will be returned first.
   * <p>
   * Sample usage:
   *
   * <pre>
   * Counter c = new Counter();
   * // add to the counter...
   * List biggestAbsKeys = new ArrayList(c.keySet());
   * Collections.sort(biggestAbsKeys, Counters.comparator(c, false, true));
   * List smallestEntries = new ArrayList(c.entrySet());
   * Collections.sort(smallestEntries, Counters.comparator(c, true, false));
   * </pre>
   */
  public static <E> Comparator<E> toComparator(final Counter<E> counter, final boolean ascending, final boolean useMagnitude) {
    return (o1, o2) -> {
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
    };
  }

  /**
   * A List of the keys in c, sorted from highest count to lowest.
   * So note that the default is descending!
   *
   * @return A List of the keys in c, sorted from highest count to lowest.
   */
  public static <E> List<E> toSortedList(Counter<E> c) {
    return toSortedList(c, false);
  }

  /**
   * A List of the keys in c, sorted from highest count to lowest.
   *
   * @return A List of the keys in c, sorted from highest count to lowest.
   */
  public static <E> List<E> toSortedList(Counter<E> c, boolean ascending) {
    List<E> l = new ArrayList<>(c.keySet());
    Comparator<E> comp = ascending ? toComparator(c) : toComparatorDescending(c);
    Collections.sort(l, comp);
    return l;
  }

  /**
   * A List of the keys in c, sorted from highest count to lowest.
   *
   * @return A List of the keys in c, sorted from highest count to lowest.
   */
  public static <E extends Comparable<E>> List<E> toSortedListKeyComparable(Counter<E> c) {
    List<E> l = new ArrayList<>(c.keySet());
    Comparator<E> comp = toComparatorWithKeys(c);
    Collections.sort(l, comp);
    Collections.reverse(l);
    return l;
  }

  /**
   * Converts a counter to ranks; ranks start from 0
   *
   * @return A counter where the count is the rank in the original counter
   */
  public static <E> IntCounter<E> toRankCounter(Counter<E> c) {
    IntCounter<E> rankCounter = new IntCounter<>();
    List<E> sortedList = toSortedList(c);
    for (int i = 0; i < sortedList.size(); i++) {
      rankCounter.setCount(sortedList.get(i), i);
    }
    return rankCounter;
  }

  /**
   * Converts a counter to tied ranks; ranks start from 1
   *
   * @return A counter where the count is the rank in the original counter; when values are tied, the rank is the average of the ranks of the tied values
   */
  public static <E> Counter<E> toTiedRankCounter(Counter<E> c) {
    Counter<E> rankCounter = new ClassicCounter<>();
    List<Pair<E, Double>> sortedList = toSortedListWithCounts(c);

    int i = 0;
    Iterator<Pair<E, Double>> it = sortedList.iterator();
    while(it.hasNext()) {
      Pair<E, Double> iEn = it.next();
      double icount = iEn.second();
      E iKey = iEn.first();

      List<Integer> l = new ArrayList<>();
      List<E> keys = new ArrayList<>();


      l.add(i+1);
      keys.add(iKey);

      for(int j = i +1; j < sortedList.size(); j++){
        Pair<E, Double> jEn = sortedList.get(j);
        if( icount == jEn.second()){
          l.add(j+1);
          keys.add(jEn.first());
        }else
          break;
      }

      if(l.size() > 1){
        double sum = 0;
        for(Integer d: l)
          sum += d;
        double avgRank = sum/l.size();
        for(int k = 0; k < l.size(); k++){
          rankCounter.setCount(keys.get(k), avgRank);
          if(k != l.size()-1 && it.hasNext())
            it.next();
          i++;
        }
      }else{
        rankCounter.setCount(iKey, i+1);
        i++;
      }
    }
    return rankCounter;
  }

  public static <E> List<Pair<E, Double>> toDescendingMagnitudeSortedListWithCounts(Counter<E> c) {
    List<E> keys = new ArrayList<>(c.keySet());
    Collections.sort(keys, toComparator(c, false, true));
    List<Pair<E, Double>> l = new ArrayList<>(keys.size());

    for (E key : keys) {
      l.add(new Pair<>(key, c.getCount(key)));
    }

    return l;
  }

  /**
   * A List of the keys in c, sorted from highest count to lowest, paired with
   * counts
   *
   * @return A List of the keys in c, sorted from highest count to lowest.
   */
  public static <E> List<Pair<E, Double>> toSortedListWithCounts(Counter<E> c) {
    List<Pair<E, Double>> l = new ArrayList<>(c.size());
    for (E e : c.keySet()) {
      l.add(new Pair<>(e, c.getCount(e)));
    }
    // descending order
    Collections.sort(l, (a, b) -> Double.compare(b.second, a.second));
    return l;
  }

  /**
   * A List of the keys in c, sorted by the given comparator, paired with
   * counts.
   *
   * @return A List of the keys in c, sorted from highest count to lowest.
   */
  public static <E> List<Pair<E, Double>> toSortedListWithCounts(Counter<E> c, Comparator<Pair<E,Double>> comparator) {
    List<Pair<E, Double>> l = new ArrayList<>(c.size());
    for (E e : c.keySet()) {
      l.add(new Pair<>(e, c.getCount(e)));
    }
    // descending order
    Collections.sort(l, comparator);
    return l;
  }

  /**
   * Returns a {@link edu.stanford.nlp.util.PriorityQueue} whose elements are
   * the keys of Counter c, and the score of each key in c becomes its priority.
   *
   * @param c Input Counter
   * @return A PriorityQueue where the count is a key's priority
   */
  // TODO: rewrite to use entrySet()
  public static <E> edu.stanford.nlp.util.PriorityQueue<E> toPriorityQueue(Counter<E> c) {
    edu.stanford.nlp.util.PriorityQueue<E> queue = new BinaryHeapPriorityQueue<>();
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
   * Returns a Counter that is the union of the two Counters passed in (counts
   * are added).
   *
   * @return A Counter that is the union of the two Counters passed in (counts
   *         are added).
   */
  @SuppressWarnings("unchecked")
  public static <E, C extends Counter<E>> C union(C c1, C c2) {
    C result = (C) c1.getFactory().create();
    addInPlace(result, c1);
    addInPlace(result, c2);
    return result;
  }

  /**
   * Returns a counter that is the intersection of c1 and c2. If both c1 and c2
   * contain a key, the min of the two counts is used.
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
   * Returns the Jaccard Coefficient of the two counters. Calculated as |c1
   * intersect c2| / ( |c1| + |c2| - |c1 intersect c2|
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
    if (c1.size() > c2.size()) {
      Counter<E> tmpCnt = c1;
      c1 = c2;
      c2 = tmpCnt;
    }
    for (E key : c1.keySet()) {
      double count1 = c1.getCount(key);
      if (Double.isNaN(count1) || Double.isInfinite(count1)) {
        throw new RuntimeException("Counters.dotProduct infinite or NaN value for key: " + key + '\t' + c1.getCount(key) + '\t' + c2.getCount(key));
      }
      if (count1 != 0.0) {
        double count2 = c2.getCount(key);
        if (Double.isNaN(count2) || Double.isInfinite(count2)) {
          throw new RuntimeException("Counters.dotProduct infinite or NaN value for key: " + key + '\t' + c1.getCount(key) + '\t' + c2.getCount(key));
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
   * Returns the product of Counter c and double[] a, using Index idx to map
   * entries in C onto a.
   *
   * @return The product of c and a.
   */
  public static <E> double dotProduct(Counter<E> c, double[] a, Index<E> idx) {
    double dotProd = 0.0;
    for (Map.Entry<E, Double> entry : c.entrySet()) {
      int keyIdx = idx.indexOf(entry.getKey());
      if (keyIdx >= 0) {
        dotProd += entry.getValue() * a[keyIdx];
      }
    }
    return dotProd;
  }

  public static <E> double sumEntries(Counter<E> c1, Collection<E> entries) {
    double dotProd = 0.0;
    for (E entry : entries) {
      dotProd += c1.getCount(entry);
    }
    return dotProd;
  }


  public static <E> Counter<E> add(Counter<E> c1, Collection<E> c2) {
    Counter<E> result = c1.getFactory().create();
    addInPlace(result, c1);
    for (E key : c2) {
      result.incrementCount(key, 1);
    }
    return result;
  }

  public static <E> Counter<E> add(Counter<E> c1, Counter<E> c2) {
    Counter<E> result = c1.getFactory().create();
    for (E key : Sets.union(c1.keySet(), c2.keySet())) {
      result.setCount(key, c1.getCount(key) + c2.getCount(key));
    }
    retainNonZeros(result);
    return result;
  }

  /**
   * increments every key in the counter by value
   */
  public static <E> Counter<E> add(Counter<E> c1, double value) {
    Counter<E> result = c1.getFactory().create();
    for (E key : c1.keySet()) {
      result.setCount(key, c1.getCount(key) + value);
    }
    return result;
  }

  /**
   * This method does not check entries for NAN or INFINITY values in the
   * doubles returned. It also only iterates over the counter with the smallest
   * number of keys to help speed up computation. Pair this method with
   * normalizing your counters before hand and you have a reasonably quick
   * implementation of cosine.
   *
   * @param <E>
   * @param c1
   * @param c2
   * @return The dot product of the two counter (as vectors)
   */
  public static <E> double optimizedDotProduct(Counter<E> c1, Counter<E> c2) {
    int size1 = c1.size();
    int size2 = c2.size();
    if (size1 < size2) {
      return getDotProd(c1, c2);
    } else {
      return getDotProd(c2, c1);
    }
  }

  private static <E> double getDotProd(Counter<E> c1, Counter<E> c2) {
    double dotProd = 0.0;
    for (E key : c1.keySet()) {
      double count1 = c1.getCount(key);
      if (count1 != 0.0) {
        double count2 = c2.getCount(key);
        if (count2 != 0.0)
          dotProd += (count1 * count2);
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
   * Returns c1 divided by c2. Note that this can create NaN if c1 has non-zero
   * counts for keys that c2 has zero counts.
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
   * Returns c1 divided by c2. Safe - will not calculate scores for keys that are zero or that do not exist in c2
   *
   * @return c1 divided by c2.
   */
  public static <E> Counter<E> divisionNonNaN(Counter<E> c1, Counter<E> c2) {
    Counter<E> result = c1.getFactory().create();
    for (E key : Sets.union(c1.keySet(), c2.keySet())) {
      if(c2.getCount(key) != 0)
        result.setCount(key, c1.getCount(key) / c2.getCount(key));
    }
    return result;
  }


  /**
   * Calculates the entropy of the given counter (in bits). This method
   * internally uses normalized counts (so they sum to one), but the value
   * returned is meaningless if some of the counts are negative.
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
   * Note that this implementation doesn't normalize the "from" Counter. It
   * does, however, normalize the "to" Counter. Result is meaningless if any of
   * the counts are negative.
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
   * Calculates the KL divergence between the two counters. That is, it
   * calculates KL(from || to). This method internally uses normalized counts
   * (so they sum to one), but the value returned is meaningless if any of the
   * counts are negative. In other words, how well can c1 be represented by c2.
   * if there is some value in c1 that gets zero prob in c2, then return
   * positive infinity.
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
   * Calculates the Jensen-Shannon divergence between the two counters. That is,
   * it calculates 1/2 [KL(c1 || avg(c1,c2)) + KL(c2 || avg(c1,c2))] .
   * This code assumes that the Counters have only non-negative values in them.
   *
   * @return The Jensen-Shannon divergence between the distributions
   */
  public static <E> double jensenShannonDivergence(Counter<E> c1, Counter<E> c2) {
    // need to normalize the counters first before averaging them! Else buggy if not a probability distribution
    Counter<E> d1 = asNormalizedCounter(c1);
    Counter<E> d2 = asNormalizedCounter(c2);
    Counter<E> average = average(d1, d2);
    double kl1 = klDivergence(d1, average);
    double kl2 = klDivergence(d2, average);
    return (kl1 + kl2) / 2.0;
  }

  /**
   * Calculates the skew divergence between the two counters. That is, it
   * calculates KL(c1 || (c2*skew + c1*(1-skew))) . In other words, how well can
   * c1 be represented by a "smoothed" c2.
   *
   * @return The skew divergence between the distributions
   */
  public static <E> double skewDivergence(Counter<E> c1, Counter<E> c2, double skew) {
    Counter<E> d1 = asNormalizedCounter(c1);
    Counter<E> d2 = asNormalizedCounter(c2);
    Counter<E> average = linearCombination(d2, skew, d1, (1.0 - skew));
    return klDivergence(d1, average);
  }

  /**
   * Return the l2 norm (Euclidean vector length) of a Counter.
   * <i>Implementation note:</i> The method name favors legibility of the L over
   * the convention of using lowercase names for methods.
   *
   * @param c The Counter
   * @return Its length
   */
  public static <E, C extends Counter<E>> double L2Norm(C c) {
    return Math.sqrt(Counters.sumSquares(c));
  }

  /**
   * Return the sum of squares (squared L2 norm).
   *
   * @param c The Counter
   * @return the L2 norm of the values in c
   */
  public static <E, C extends Counter<E>> double sumSquares(C c) {
    double lenSq = 0.0;
    for (E key : c.keySet()) {
      double count = c.getCount(key);
      lenSq += (count * count);
    }
    return lenSq;
  }

  /**
   * Return the L1 norm of a counter. <i>Implementation note:</i> The method
   * name favors legibility of the L over the convention of using lowercase
   * names for methods.
   *
   * @param c The Counter
   * @return Its length
   */
  public static <E, C extends Counter<E>> double L1Norm(C c) {
    double sumAbs = 0.0;
    for (E key : c.keySet()) {
      double count = c.getCount(key);
      if (count != 0.0) {
        sumAbs += Math.abs(count);
      }
    }
    return sumAbs;
  }

  /**
   * L2 normalize a counter.
   *
   * @param c The {@link Counter} to be L2 normalized. This counter is not
   *          modified.
   * @return A new l2-normalized Counter based on c.
   */
  public static <E, C extends Counter<E>> C L2Normalize(C c) {
    return scale(c, 1.0 / L2Norm(c));
  }

  /**
   * L2 normalize a counter in place.
   *
   * @param c The {@link Counter} to be L2 normalized. This counter is modified
   * @return the passed in counter l2-normalized
   */
  public static <E> Counter<E> L2NormalizeInPlace(Counter<E> c) {
    return multiplyInPlace(c, 1.0 / L2Norm(c));
  }

  /**
   * For counters with large # of entries, this scales down each entry in the
   * sum, to prevent an extremely large sum from building up and overwhelming
   * the max double. This may also help reduce error by preventing loss of SD's
   * with extremely large values.
   *
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
      sqrSum += Math.pow(count / maxVal, 2);
    }
    return maxVal * Math.sqrt(sqrSum);
  }

  /**
   * L2 normalize a counter, using the "safer" L2 normalizer.
   *
   * @param c The {@link Counter} to be L2 normalized. This counter is not
   *          modified.
   * @return A new L2-normalized Counter based on c.
   */
  public static <E, C extends Counter<E>> C saferL2Normalize(C c) {
    return scale(c, 1.0 / saferL2Norm(c));
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
   * Returns a new Counter with counts averaged from the two given Counters. The
   * average Counter will contain the union of keys in both source Counters, and
   * each count will be the average of the two source counts for that key, where
   * as usual a missing count in one Counter is treated as count 0.
   *
   * @return A new counter with counts that are the mean of the resp. counts in
   *         the given counters.
   */
  public static <E> Counter<E> average(Counter<E> c1, Counter<E> c2) {
    Counter<E> average = c1.getFactory().create();
    Set<E> allKeys = Generics.newHashSet(c1.keySet());
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

  public static <T1, T2> double pointwiseMutualInformation(Counter<T1> var1Distribution, Counter<T2> var2Distribution, Counter<Pair<T1, T2>> jointDistribution, Pair<T1, T2> values) {
    double var1Prob = var1Distribution.getCount(values.first);
    double var2Prob = var2Distribution.getCount(values.second);
    double jointProb = jointDistribution.getCount(values);
    double pmi = Math.log(jointProb) - Math.log(var1Prob) - Math.log(var2Prob);
    return pmi / LOG_E_2;
  }

  /**
   * Calculate h-Index (Hirsch, 2005) of an author.
   *
   * A scientist has index h if h of their Np papers have at least h citations
   * each, and the other (Np − h) papers have at most h citations each.
   *
   * @param citationCounts
   *          Citation counts for each of the articles written by the author.
   *          The keys can be anything, but the values should be integers.
   * @return The h-Index of the author.
   */
  public static <E> int hIndex(Counter<E> citationCounts) {
    Counter<Integer> countCounts = new ClassicCounter<>();
    for (double value : citationCounts.values()) {
      for (int i = 0; i <= value; ++i) {
        countCounts.incrementCount(i);
      }
    }
    List<Integer> citationCountValues = CollectionUtils.sorted(countCounts.keySet());
    Collections.reverse(citationCountValues);
    for (int citationCount : citationCountValues) {
      double occurrences = countCounts.getCount(citationCount);
      if (occurrences >= citationCount) {
        return citationCount;
      }
    }
    return 0;
  }

  @SuppressWarnings("unchecked")
  public static <E, C extends Counter<E>> C perturbCounts(C c, Random random, double p) {
    C result = (C) c.getFactory().create();
    for (E key : c.keySet()) {
      double count = c.getCount(key);
      // inverse of CDF for exponential distribution
      // (1.0 - random) to avoid taking log(0) (probably?)
      double noise = -Math.log(1.0 - random.nextDouble());
      // log.info("noise=" + noise);
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
    printCounterComparison(a, b, new PrintWriter(out, true));
  }

  /**
   * Prints one or more lines (with a newline at the end) describing the
   * difference between the two Counters. Great for debugging.
   *
   */
  public static <E> void printCounterComparison(Counter<E> a, Counter<E> b, PrintWriter out) {
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
    Set<E> rest = Generics.newHashSet(b.keySet());
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
    Counter<Double> result = new ClassicCounter<>();
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
   * @return A new Counter which is the argument scaled by the given scale
   *         factor.
   */
  @SuppressWarnings("unchecked")
  public static <E, C extends Counter<E>> C scale(C c, double s) {
    C scaled = (C) c.getFactory().create();
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
   * @return A new Counter which is the argument scaled by the given scale
   *         factor.
   */
  @SuppressWarnings("unchecked")
  public static <E, C extends Counter<E>> C tfLogScale(C c, double base) {
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
    List<E> keyList = new ArrayList<>(c.keySet());
    Collections.sort(keyList);
    for (E o : keyList) {
      System.out.println(o + ":" + c.getCount(o));
    }
  }

  /**
   * Loads a Counter from a text file. File must have the format of one
   * key/count pair per line, separated by whitespace.
   *
   * @param filename The path to the file to load the Counter from
   * @param c The Class to instantiate each member of the set. Must have a
   *          String constructor.
   * @return The counter loaded from the file.
   */
  public static <E> ClassicCounter<E> loadCounter(String filename, Class<E> c) throws RuntimeException {
    ClassicCounter<E> counter = new ClassicCounter<>();
    loadIntoCounter(filename, c, counter);
    return counter;
  }

  /**
   * Loads a Counter from a text file. File must have the format of one
   * key/count pair per line, separated by whitespace.
   *
   * @param filename The path to the file to load the Counter from
   * @param c The Class to instantiate each member of the set. Must have a
   *          String constructor.
   * @return The counter loaded from the file.
   */
  public static <E> IntCounter<E> loadIntCounter(String filename, Class<E> c) throws Exception {
    IntCounter<E> counter = new IntCounter<>();
    loadIntoCounter(filename, c, counter);
    return counter;
  }

  /**
   * Loads a file into an GenericCounter.
   */
  private static <E> void loadIntoCounter(String filename, Class<E> c, Counter<E> counter) throws RuntimeException {
    try {
      Constructor<E> m = c.getConstructor(String.class);
      BufferedReader in = IOUtils.readerFromString(filename);
      for (String line; (line = in.readLine()) != null;) {
        String[] tokens = line.trim().split("\\s+");
        if (tokens.length != 2) throw new RuntimeException();
        double value = Double.parseDouble(tokens[1]);
        counter.setCount(m.newInstance(tokens[0]), value);
      }
      in.close();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Saves a Counter as one key/count pair per line separated by white space to
   * the given OutputStream. Does not close the stream.
   */
  public static <E> void saveCounter(Counter<E> c, OutputStream stream) {
    PrintStream out = new PrintStream(stream);
    for (E key : c.keySet()) {
      out.println(key + " " + c.getCount(key));
    }
  }

  /**
   * Saves a Counter to a text file. Counter written as one key/count pair per
   * line, separated by whitespace.
   */
  public static <E> void saveCounter(Counter<E> c, String filename) throws IOException {
    FileOutputStream fos = new FileOutputStream(filename);
    saveCounter(c, fos);
    fos.close();
  }

  public static <T1, T2> TwoDimensionalCounter<T1, T2> load2DCounter(String filename, Class<T1> t1, Class<T2> t2) throws RuntimeException {
    try {
      TwoDimensionalCounter<T1, T2> tdc = new TwoDimensionalCounter<>();
      loadInto2DCounter(filename, t1, t2, tdc);
      return tdc;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static <T1, T2> void loadInto2DCounter(String filename, Class<T1> t1, Class<T2> t2, TwoDimensionalCounter<T1, T2> tdc) throws RuntimeException {
    try {
      Constructor<T1> m1 = t1.getConstructor(String.class);
      Constructor<T2> m2 = t2.getConstructor(String.class);
      // instead of new BufferedReader(new FileReader(filename));
      BufferedReader in = IOUtils.readerFromString(filename);
      for (String line; (line = in.readLine()) != null;) {
        String[] tuple = line.trim().split("\t");
        String outer = tuple[0];
        String inner = tuple[1];
        String valStr = tuple[2];
        tdc.setCount(m1.newInstance(outer.trim()), m2.newInstance(inner.trim()), Double.parseDouble(valStr.trim()));
      }
      in.close();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static <T1, T2> void loadIncInto2DCounter(String filename, Class<T1> t1, Class<T2> t2,
                                                   TwoDimensionalCounterInterface<T1, T2> tdc) throws RuntimeException {
    try {
      Constructor<T1> m1 = t1.getConstructor(String.class);
      Constructor<T2> m2 = t2.getConstructor(String.class);
      // new BufferedReader(new FileReader(filename));
      BufferedReader in = IOUtils.readerFromString(filename);
      for (String line; (line = in.readLine()) != null;) {
        String[] tuple = line.trim().split("\t");
        String outer = tuple[0];
        String inner = tuple[1];
        String valStr = tuple[2];
        tdc.incrementCount(m1.newInstance(outer.trim()), m2.newInstance(inner.trim()), Double.parseDouble(valStr.trim()));
      }
      in.close();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static <T1, T2> void save2DCounter(TwoDimensionalCounter<T1, T2> tdc, String filename) throws IOException {
    PrintWriter out = new PrintWriter(new FileWriter(filename));
    for (T1 outer : tdc.firstKeySet()) {
      for (T2 inner : tdc.secondKeySet()) {
        out.println(outer + "\t" + inner + '\t' + tdc.getCount(outer, inner));
      }
    }
    out.close();
  }

  public static <T1, T2> void save2DCounterSorted(TwoDimensionalCounterInterface<T1, T2> tdc, String filename) throws IOException {
    PrintWriter out = new PrintWriter(new FileWriter(filename));
    for (T1 outer : tdc.firstKeySet()) {
      Counter<T2> c = tdc.getCounter(outer);
      List<T2> keys = Counters.toSortedList(c);
      for (T2 inner : keys) {
        out.println(outer + "\t" + inner + '\t' + c.getCount(inner));
      }
    }
    out.close();
  }

  /**
   * Serialize a counter into an efficient string TSV
   * @param c The counter to serialize
   * @param filename The file to serialize to
   * @param minMagnitude Ignore values under this magnitude
   * @throws IOException
   *
   * @see Counters#deserializeStringCounter(String)
   */
  public static void serializeStringCounter(Counter<String> c,
                                            String filename,
                                            double minMagnitude) throws IOException {
    PrintWriter writer = IOUtils.getPrintWriter(filename);
    for (Entry<String, Double> entry : c.entrySet()) {
      if (Math.abs(entry.getValue()) < minMagnitude) { continue; }
      Triple<Boolean, Long, Integer> parts = SloppyMath.segmentDouble(entry.getValue());
      writer.println(
          entry.getKey().replace('\t', 'ߝ') + "\t" +
              (parts.first ? '-' : '+') + "\t" +
              parts.second + "\t" +
              parts.third
      );
    }
    writer.close();
  }

  /** @see Counters#serializeStringCounter(Counter, String, double) */
  public static void serializeStringCounter(Counter<String> c,
                                            String filename) throws IOException {
    serializeStringCounter(c, filename, 0.0);
  }


  /**
   * Read a Counter from a serialized file
   * @param filename The file to read from
   *
   * @see Counters#serializeStringCounter(Counter, String, double)
   */
  public static ClassicCounter<String> deserializeStringCounter(String filename) throws IOException {
    String[] fields = new String[4];
    try (BufferedReader reader = IOUtils.readerFromString(filename)) {
      String line;
      ClassicCounter<String> counts = new ClassicCounter<>(1000000);
      while ((line = reader.readLine()) != null) {
        StringUtils.splitOnChar(fields, line, '\t');
        long mantissa = SloppyMath.parseInt(fields[2]);
        int exponent = (int) SloppyMath.parseInt(fields[3]);
        double value = SloppyMath.parseDouble(fields[1].equals("-"), mantissa, exponent);
        counts.setCount(fields[0], value);
      }
      return counts;
    }
  }



  public static <T> void serializeCounter(Counter<T> c, String filename) throws IOException {
    // serialize to file
    ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(filename)));
    out.writeObject(c);
    out.close();
  }

  public static <T> ClassicCounter<T> deserializeCounter(String filename) throws Exception {
    // reconstitute
    ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(filename)));
    ClassicCounter<T> c = ErasureUtils.uncheckedCast(in.readObject());
    in.close();
    return c;
  }

  /**
   * Returns a string representation of a Counter, displaying the keys and their
   * counts in decreasing order of count. At most k keys are displayed.
   *
   * Note that this method subsumes many of the other toString methods, e.g.:
   *
   * toString(c, k) and toBiggestValuesFirstString(c, k) =&gt; toSortedString(c, k,
   * "%s=%f", ", ", "[%s]")
   *
   * toVerticalString(c, k) =&gt; toSortedString(c, k, "%2$g\t%1$s", "\n", "%s\n")
   *
   * @param counter A Counter.
   * @param k The number of keys to include. Use Integer.MAX_VALUE to include
   *          all keys.
   * @param itemFormat
   *          The format string for key/count pairs, where the key is first and
   *          the value is second. To display the value first, use argument
   *          indices, e.g. "%2$f %1$s".
   * @param joiner The string used between pairs of key/value strings.
   * @param wrapperFormat
   *          The format string for wrapping text around the joined items, where
   *          the joined item string value is "%s".
   * @return The top k values from the Counter, formatted as specified.
   */
  public static <T> String toSortedString(Counter<T> counter, int k, String itemFormat, String joiner, String wrapperFormat) {
    PriorityQueue<T> queue = toPriorityQueue(counter);
    List<String> strings = new ArrayList<>();
    for (int rank = 0; rank < k && !queue.isEmpty(); ++rank) {
      T key = queue.removeFirst();
      double value = counter.getCount(key);
      strings.add(String.format(itemFormat, key, value));
    }
    return String.format(wrapperFormat, StringUtils.join(strings, joiner));
  }

  /**
   * Returns a string representation of a Counter, displaying the keys and their
   * counts in decreasing order of count. At most k keys are displayed.
   *
   * @param counter A Counter.
   * @param k
   *          The number of keys to include. Use Integer.MAX_VALUE to include
   *          all keys.
   * @param itemFormat
   *          The format string for key/count pairs, where the key is first and
   *          the value is second. To display the value first, use argument
   *          indices, e.g. "%2$f %1$s".
   * @param joiner
   *          The string used between pairs of key/value strings.
   * @return The top k values from the Counter, formatted as specified.
   */
  public static <T> String toSortedString(Counter<T> counter, int k, String itemFormat, String joiner) {
    return toSortedString(counter, k, itemFormat, joiner, "%s");
  }

  /**
   * Returns a string representation of a Counter, where (key, value) pairs are
   * sorted by key, and formatted as specified.
   *
   * @param counter The Counter.
   * @param itemFormat
   *          The format string for key/count pairs, where the key is first and
   *          the value is second. To display the value first, use argument
   *          indices, e.g. "%2$f %1$s".
   * @param joiner
   *          The string used between pairs of key/value strings.
   * @param wrapperFormat
   *          The format string for wrapping text around the joined items, where
   *          the joined item string value is "%s".
   * @return The Counter, formatted as specified.
   */
  public static <T extends Comparable<T>> String toSortedByKeysString(Counter<T> counter, String itemFormat, String joiner, String wrapperFormat) {
    List<String> strings = new ArrayList<>();
    for (T key : CollectionUtils.sorted(counter.keySet())) {
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
    // */
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

  /**
   * Pretty print a Counter. This one has more flexibility in formatting, and
   * doesn't sort the keys.
   */
  public static <E> String toString(Counter<E> counter, NumberFormat nf, String preAppend, String postAppend, String keyValSeparator, String itemSeparator) {
    StringBuilder sb = new StringBuilder();
    sb.append(preAppend);
    // List<E> list = new ArrayList<E>(map.keySet());
    // try {
    // Collections.sort(list); // see if it can be sorted
    // } catch (Exception e) {
    // }
    for (Iterator<E> iter = counter.keySet().iterator(); iter.hasNext();) {
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
    PriorityQueue<E> largestK = new BinaryHeapPriorityQueue<>();
    // TODO: Is there any reason the original (commented out) line is better
    // than the one replacing it?
    // while (largestK.size() < k && ((Iterator<E>)pq).hasNext()) {
    while (largestK.size() < k && !pq.isEmpty()) {
      double firstScore = pq.getPriority(pq.getFirst());
      E first = pq.removeFirst();
      largestK.changePriority(first, firstScore);
    }
    return largestK.toString();
  }

  public static <T> String toBiggestValuesFirstString(Counter<Integer> c, int k, Index<T> index) {
    PriorityQueue<Integer> pq = toPriorityQueue(c);
    PriorityQueue<T> largestK = new BinaryHeapPriorityQueue<>();
    // while (largestK.size() < k && ((Iterator)pq).hasNext()) { //same as above
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
   * Returns a {@code String} representation of the {@code k} keys
   * with the largest counts in the given {@link Counter}, using the given
   * format string.
   *
   * @param c A Counter
   * @param k How many keys to print
   * @param fmt A format string, such as "%.0f\t%s" (do not include final "%n").
   *            If swap is false, you will get val, key as arguments, if true, key, val.
   * @param swap Whether the count should appear after the key
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
   * @return Returns the maximum element of c that is within the restriction
   *         Collection
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
    if (index.size() < counts.length)
      throw new IllegalArgumentException("Index not large enough to name all the array elements!");
    Counter<T> c = new ClassicCounter<>();
    for (int i = 0; i < counts.length; i++) {
      if (counts[i] != 0.0)
        c.setCount(index.get(i), counts[i]);
    }
    return c;
  }

  /**
   * Turns the given map and index into a counter instance. For each entry in
   * counts, its key is converted to a counter key via lookup in the given
   * index.
   */
  public static <E> Counter<E> toCounter(Map<Integer, ? extends Number> counts, Index<E> index) {

    Counter<E> counter = new ClassicCounter<>();
    for (Map.Entry<Integer, ? extends Number> entry : counts.entrySet()) {
      counter.setCount(index.get(entry.getKey()), entry.getValue().doubleValue());
    }
    return counter;
  }

  /**
   * Convert a counter to an array using a specified key index. Infer the dimension of
   * the returned vector from the index.
   */
  public static <E> double[] asArray(Counter<E> counter, Index<E> index) {
    return Counters.asArray(counter, index, index.size());
  }

  /**
   * Convert a counter to an array using a specified key index. This method does *not* expand
   * the index, so all keys in the set keys(counter) - keys(index) are not added to the
   * output array. Also note that if counter is being used as a sparse array, the result
   * will be a dense array with zero entries.
   *
   * @return the values corresponding to the index
   */
  public static <E> double[] asArray(Counter<E> counter, Index<E> index, int dimension) {
    if (index.size() == 0) {
      throw new IllegalArgumentException("Empty index");
    }
    Set<E> keys = counter.keySet();
    double[] array = new double[dimension];
    for (E key : keys) {
      int i = index.indexOf(key);
      if (i >= 0) {
        array[i] = counter.getCount(key);
      }
    }
    return array;
  }

  /**
   * Convert a counter to an array, the order of the array is random
   */
  public static <E> double[] asArray(Counter<E> counter) {
    Set<E> keys = counter.keySet();
    double[] array = new double[counter.size()];
    int i = 0;
    for (E key : keys) {
      array[i] = counter.getCount(key);
      i++;
    }
    return array;
  }


  /**
   * Creates a new TwoDimensionalCounter where all the counts are scaled by d.
   * Internally, uses Counters.scale();
   *
   * @return The TwoDimensionalCounter
   */
  public static <T1, T2> TwoDimensionalCounter<T1, T2> scale(TwoDimensionalCounter<T1, T2> c, double d) {
    TwoDimensionalCounter<T1, T2> result = new TwoDimensionalCounter<>(c.getOuterMapFactory(), c.getInnerMapFactory());
    for (T1 key : c.firstKeySet()) {
      ClassicCounter<T2> ctr = c.getCounter(key);
      result.setCounter(key, scale(ctr, d));
    }
    return result;
  }

  static final Random RAND = new Random();

  /**
   * Does not assumes c is normalized.
   *
   * @return A sample from c
   */
  public static <T> T sample(Counter<T> c, Random rand) {
    // OMITTED: Seems like there should be a way to directly check if T is comparable
    // Set<T> keySet = c.keySet();
    // if (!keySet.isEmpty() && keySet.iterator().next() instanceof Comparable) {
    //   List l = new ArrayList<T>(keySet);
    //   Collections.sort(l);
    //   objects = l;
    // } else {
    //   throw new RuntimeException("Results won't be stable since Counters keys are comparable.");
    // }
    if (rand == null) rand = RAND;
    double r = rand.nextDouble() * c.totalCount();
    double total = 0.0;

    for (T t : c.keySet()) { // arbitrary ordering, but presumably stable
      total += c.getCount(t);
      if (total >= r)
        return t;
    }
    // only chance of reaching here is if c isn't properly normalized, or if
    // double math makes total<1.0
    return c.keySet().iterator().next();
  }

  /**
   * Does not assumes c is normalized.
   *
   * @return A sample from c
   */

  public static <T> T sample(Counter<T> c) {
    return sample(c, null);
  }

  /**
   * Returns a counter where each element corresponds to the normalized count of
   * the corresponding element in c raised to the given power.
   */
  public static <E> Counter<E> powNormalized(Counter<E> c, double temp) {
    Counter<E> d = c.getFactory().create();
    double total = c.totalCount();
    for (E e : c.keySet()) {
      d.setCount(e, Math.pow(c.getCount(e) / total, temp));
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
   * Default equality comparison for two counters potentially backed by
   * alternative implementations.
   */
  public static <E> boolean equals(Counter<E> o1, Counter<E> o2) {
    return equals(o1, o2, 0.0);
  }

  /**
   * Equality comparison between two counters, allowing for a tolerance fudge factor.
   */
  public static <E> boolean equals(Counter<E> o1, Counter<E> o2, double tolerance) {
    if (o1 == o2) {
      return true;
    }

    if (Math.abs(o1.totalCount() - o2.totalCount()) > tolerance) {
      return false;
    }

    if (!o1.keySet().equals(o2.keySet())) {
      return false;
    }

    for (E key : o1.keySet()) {
      if (Math.abs(o1.getCount(key) - o2.getCount(key)) > tolerance) {
        return false;
      }
    }

    return true;

  }

  /**
   * Returns unmodifiable view of the counter. changes to the underlying Counter
   * are written through to this Counter.
   *
   * @param counter
   *          The counter
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

      public double getCount(Object key) {
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
        return Collections.unmodifiableSet(new AbstractSet<Map.Entry<T, Double>>() {
          @Override
          public Iterator<Entry<T, Double>> iterator() {
            return new Iterator<Entry<T, Double>>() {
              final Iterator<Entry<T, Double>> inner = counter.entrySet().iterator();

              public boolean hasNext() {
                return inner.hasNext();
              }

              public Entry<T, Double> next() {
                return new Map.Entry<T, Double>() {
                  final Entry<T, Double> e = inner.next();

                  @Override
                  public T getKey() {
                    return e.getKey();
                  }

                  @Override
                  @SuppressWarnings( { "UnnecessaryBoxing", "UnnecessaryUnboxing" })
                  public Double getValue() {
                    return Double.valueOf(e.getValue().doubleValue());
                  }

                  @Override
                  public Double setValue(Double value) {
                    throw new UnsupportedOperationException();
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
            return counter.size();
          }
        });
      }

      @Override
      public void setDefaultReturnValue(double rv) {
        throw new UnsupportedOperationException();
      }

      @Override
      public double defaultReturnValue() {
        return counter.defaultReturnValue();
      }

      /**
       * {@inheritDoc}
       */
      public void prettyLog(RedwoodChannels channels, String description) {
        PrettyLogger.log(channels, description, asMap(this));
      }
    };
  } // end unmodifiableCounter()

  /**
   * Returns a counter whose keys are the elements in this priority queue, and
   * whose counts are the priorities in this queue. In the event there are
   * multiple instances of the same element in the queue, the counter's count
   * will be the sum of the instances' priorities.
   *
   */
  public static <E> Counter<E> asCounter(FixedPrioritiesPriorityQueue<E> p) {
    FixedPrioritiesPriorityQueue<E> pq = p.clone();
    ClassicCounter<E> counter = new ClassicCounter<>();
    while (pq.hasNext()) {
      double priority = pq.getPriority();
      E element = pq.next();
      counter.incrementCount(element, priority);
    }
    return counter;
  }

  /**
   * Returns a counter view of the given map. Infers the numeric type of the
   * values from the first element in map.values().
   */
  @SuppressWarnings("unchecked")
  public static <E, N extends Number> Counter<E> fromMap(final Map<E, N> map) {
    if (map.isEmpty()) {
      throw new IllegalArgumentException("Map must have at least one element" + " to infer numeric type; add an element first or use e.g." + " fromMap(map, Integer.class)");
    }
    return fromMap(map, (Class) map.values().iterator().next().getClass());
  }

  /**
   * Returns a counter view of the given map. The type parameter is the type of
   * the values in the map, which because of Java's generics type erasure, can't
   * be discovered by reflection if the map is currently empty.
   */
  public static <E, N extends Number> Counter<E> fromMap(final Map<E, N> map, final Class<N> type) {
    // get our initial total
    double initialTotal = 0.0;
    for (Map.Entry<E, N> entry : map.entrySet()) {
      initialTotal += entry.getValue().doubleValue();
    }

    // and pass it in to the returned inner class with a final variable
    final double initialTotalFinal = initialTotal;

    return new AbstractCounter<E>() {
      double total = initialTotalFinal;
      double defRV = 0.0;

      @Override
      public void clear() {
        map.clear();
        total = 0.0;
      }

      @Override
      public boolean containsKey(E key) {
        return map.containsKey(key);
      }

      @Override
      public void setDefaultReturnValue(double rv) {
        defRV = rv;
      }

      @Override
      public double defaultReturnValue() {
        return defRV;
      }

      @Override
      @SuppressWarnings("unchecked")
      public boolean equals(Object o) {
        if (this == o) {
          return true;
        } else if (!(o instanceof Counter)) {
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
        return new AbstractSet<Entry<E, Double>>() {
          Set<Entry<E, N>> entries = map.entrySet();

          @Override
          public Iterator<Entry<E, Double>> iterator() {
            return new Iterator<Entry<E, Double>>() {
              Iterator<Entry<E, N>> it = entries.iterator();
              Entry<E, N> lastEntry; // = null;

              public boolean hasNext() {
                return it.hasNext();
              }

              public Entry<E, Double> next() {
                final Entry<E, N> entry = it.next();
                lastEntry = entry;

                return new Entry<E, Double>() {
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
                      rv = ErasureUtils.<Entry<E, Double>> uncheckedCast(entry).setValue(value);
                    } else if (type == Integer.class) {
                      rv = ErasureUtils.<Entry<E, Integer>> uncheckedCast(entry).setValue(value.intValue());
                    } else if (type == Float.class) {
                      rv = ErasureUtils.<Entry<E, Float>> uncheckedCast(entry).setValue(value.floatValue());
                    } else if (type == Long.class) {
                      rv = ErasureUtils.<Entry<E, Long>> uncheckedCast(entry).setValue(value.longValue());
                    } else if (type == Short.class) {
                      rv = ErasureUtils.<Entry<E, Short>> uncheckedCast(entry).setValue(value.shortValue());
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

      public double getCount(Object key) {
        final Number value = map.get(key);
        return value != null ? value.doubleValue() : defRV;
      }

      public Factory<Counter<E>> getFactory() {
        return new Factory<Counter<E>>() {

          private static final long serialVersionUID = -4063129407369590522L;

          public Counter<E> create() {
            // return a HashMap backed by the same numeric type to
            // keep the precision of the returned counter consistent with
            // this one's precision
            return fromMap(Generics.<E, N>newHashMap(), type);
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
          lastValue = ErasureUtils.<Map<E, Double>> uncheckedCast(map).put(key, value);
          newValue = value;
        } else if (type == Integer.class) {
          final Integer last = ErasureUtils.<Map<E, Integer>> uncheckedCast(map).put(key, (int) value);
          lastValue = last != null ? last.doubleValue() : null;
          newValue = ((int) value);
        } else if (type == Float.class) {
          final Float last = ErasureUtils.<Map<E, Float>> uncheckedCast(map).put(key, (float) value);
          lastValue = last != null ? last.doubleValue() : null;
          newValue = ((float) value);
        } else if (type == Long.class) {
          final Long last = ErasureUtils.<Map<E, Long>> uncheckedCast(map).put(key, (long) value);
          lastValue = last != null ? last.doubleValue() : null;
          newValue = ((long) value);
        } else if (type == Short.class) {
          final Short last = ErasureUtils.<Map<E, Short>> uncheckedCast(map).put(key, (short) value);
          lastValue = last != null ? last.doubleValue() : null;
          newValue = ((short) value);
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

      /**
       * {@inheritDoc}
       */
      public void prettyLog(RedwoodChannels channels, String description) {
        PrettyLogger.log(channels, description, map);
      }
    };
  } // end fromMap()

  /**
   * Returns a map view of the given counter.
   */
  public static <E> Map<E, Double> asMap(final Counter<E> counter) {
    return new AbstractMap<E, Double>() {
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
        return counter.containsKey((E) key);
      }

      @Override
      @SuppressWarnings("unchecked")
      public Double get(Object key) {
        return counter.getCount((E) key);
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
        return counter.remove((E) key);
      }

      @Override
      public Set<E> keySet() {
        return counter.keySet();
      }
    };
  }

  /**
   * Check if this counter is a uniform distribution.
   * That is, it should sum to 1.0, and every value should be equal to every other value.
   * @param distribution The distribution to check.
   * @param tolerance The tolerance for floating point error, in both the equality and total count checks.
   * @param <E> The type of the counter.
   * @return True if this counter is the uniform distribution over its domain.
   */
  public static <E> boolean isUniformDistribution(Counter<E> distribution, double tolerance) {
    double value = Double.NaN;
    double totalCount = 0.0;
    for (double val : distribution.values()) {
      if (Double.isNaN(value)) { value = val; }
      if (Math.abs(val - value) > tolerance) { return false; }
      totalCount += val;
    }
    return Math.abs(totalCount - 1.0) < tolerance;
  }

  /**
   * Default comparator for breaking ties in argmin and argmax.
   * //TODO: What type should this be?
   * // Unused, so who cares?
   * private static final Comparator<Object> hashCodeComparator =
   *   new Comparator<Object>() {
   *     public int compare(Object o1, Object o2) {
   *      return o1.hashCode() - o2.hashCode();
   *     }
   *
   *     public boolean equals(Comparator comparator) {
   *       return (comparator == this);
   *     }
   *  };
   */

  /**
   * Comparator that uses natural ordering. Returns 0 if o1 is not Comparable.
   */
  static class NaturalComparator<E> implements Comparator<E> {
    public NaturalComparator() {
    }

    @Override
    public String toString() {
      return "NaturalComparator";
    }

    @SuppressWarnings("unchecked")
    public int compare(E o1, E o2) {
      if (o1 instanceof Comparable) {
        return (((Comparable<E>) o1).compareTo(o2));
      }
      return 0; // soft-fail
    }
  }

  /**
   *
   * @param <E>
   * @param originalCounter
   * @return a copy of the original counter
   */
  public static <E> Counter<E> getCopy(Counter<E> originalCounter) {
    Counter<E> copyCounter = new ClassicCounter<>();
    copyCounter.addAll(originalCounter);
    return copyCounter;
  }

  /**
   * Places the maximum of first and second keys values in the first counter.
   * @param <E>
   */
  public static <E> void maxInPlace(Counter<E> target, Counter<E> other) {
   for(E e: CollectionUtils.union(other.keySet(), target.keySet())){
     target.setCount(e, Math.max(target.getCount(e), other.getCount(e)));
   }
  }

  /**
   * Places the minimum of first and second keys values in the first counter.
   * @param <E>
   */
  public static <E> void minInPlace(Counter<E> target, Counter<E> other){
   for(E e: CollectionUtils.union(other.keySet(), target.keySet())){
     target.setCount(e, Math.min(target.getCount(e), other.getCount(e)));
   }
  }

  /**
   * Retains the minimal set of top keys such that their count sum is more than thresholdCount.
   * @param counter
   * @param thresholdCount
   */
  public static<E> void retainTopMass(Counter<E> counter, double thresholdCount){
    PriorityQueue<E> queue = Counters.toPriorityQueue(counter);
    counter.clear();

    double mass = 0;
    while (mass < thresholdCount && !queue.isEmpty()) {
      double value = queue.getPriority();
      E key = queue.removeFirst();
      counter.setCount(key, value);
      mass += value;
    }
  }

  public static<A,B> void divideInPlace(TwoDimensionalCounter<A, B> counter, double divisor) {
    for(Entry<A, ClassicCounter<B>> c: counter.entrySet()){
      Counters.divideInPlace(c.getValue(), divisor);
    }
    counter.recomputeTotal();
  }

  public static<E> double pearsonsCorrelationCoefficient(Counter<E> x, Counter<E> y){
    double stddevX = Counters.standardDeviation(x);
    double stddevY = Counters.standardDeviation(y);
    double meanX = Counters.mean(x);
    double meanY = Counters.mean(y);
    Counter<E> t1 = Counters.add(x, -meanX);
    Counter<E> t2 = Counters.add(y, -meanY);
    Counters.divideInPlace(t1, stddevX);
    Counters.divideInPlace(t2, stddevY);
    return Counters.dotProduct(t1, t2)/ (double)(x.size() -1);
  }

  public static<E> double spearmanRankCorrelation(Counter<E> x, Counter<E> y){
    Counter<E> xrank = Counters.toTiedRankCounter(x);
    Counter<E> yrank = Counters.toTiedRankCounter(y);
    return Counters.pearsonsCorrelationCoefficient(xrank, yrank);
  }

  /**
   * ensures that counter t has all keys in keys. If the counter does not have the keys, then add the key with count value.
   * Note that it does not change counts that exist in the counter
   */
  public static<E> void ensureKeys(Counter<E> t, Collection<E> keys, double value){
    for(E k: keys){
      if(!t.containsKey(k))
        t.setCount(k, value);
    }
  }

  public static<E> List<E> topKeys(Counter<E> t, int topNum){
    List<E> list = new ArrayList<>();
    PriorityQueue<E> q = Counters.toPriorityQueue(t);
    int num = 0;
    while(!q.isEmpty() && num < topNum){
     num++;
     list.add(q.removeFirst());
    }
    return list;
  }

  public static<E> List<Pair<E, Double>> topKeysWithCounts(Counter<E> t, int topNum){
    List<Pair<E, Double>> list = new ArrayList<>();
    PriorityQueue<E> q = Counters.toPriorityQueue(t);
    int num = 0;
    while(!q.isEmpty() && num < topNum){
     num++;
     E k = q.removeFirst();
     list.add(new Pair<>(k, t.getCount(k)));
    }
    return list;
  }

  public static<E> Counter<E> getFCounter(Counter<E> precision, Counter<E> recall, double beta){
    Counter<E> fscores = new ClassicCounter<>();
    for(E k: precision.keySet()){
      fscores.setCount(k, precision.getCount(k)*recall.getCount(k)*(1+beta*beta)/(beta*beta*precision.getCount(k) + recall.getCount(k)));
    }
    return fscores;
  }

  public static <E> void transformValuesInPlace(Counter<E> counter, DoubleUnaryOperator func){
    for(E key: counter.keySet()){
      counter.setCount(key, func.applyAsDouble(counter.getCount(key)));
    }
  }

  public static<E> Counter<E> getCounts(Counter<E> c, Collection<E> keys){
    Counter<E> newcounter = new ClassicCounter<>();
    for(E k : keys)
      newcounter.setCount(k, c.getCount(k));
    return newcounter;
  }


  public static<E> void retainKeys(Counter<E> counter, Predicate<E> retainFunction) {
    Set<E> remove = new HashSet<>();
    for(Entry<E, Double> en: counter.entrySet()){
      if(!retainFunction.test(en.getKey())){
        remove.add(en.getKey());
      }
    }
    Counters.removeKeys(counter, remove);
  }

  public static<E, E2> Counter<E> flatten(Map<E2, Counter<E>> hier){
    Counter<E> flat = new ClassicCounter<>();
    for(Entry<E2, Counter<E>> en: hier.entrySet()){
      flat.addAll(en.getValue());
    }
    return flat;
  }

  /**
   * Returns true if the given counter contains only finite, non-NaN values.
   * @param counts The counter to validate.
   * @param <E> The parameterized type of the counter.
   * @return True if the counter is finite and not NaN on every value.
   */
  public static <E> boolean isFinite(Counter<E> counts) {
    for (double value : counts.values()) {
      if (Double.isInfinite(value) || Double.isNaN(value)) {
        return false;
      }
    }
    return true;
  }

}
