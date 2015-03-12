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

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.util.Factory;
import edu.stanford.nlp.util.logging.PrettyLoggable;

/**
 * An Object to double map used for keeping weights or counts for objects.
 * Utility functions are contained in
 * {@link Counters}.  The class previously known as Counter has been
 * renamed to {@link ClassicCounter}.  An alternative Counter
 * implementation, which is more memory efficient but not necessarily faster,
 * is {@link OpenAddressCounter}.
 * <p>
 * <i>Implementation note:</i> You shouldn't casually add further methods to
 * this interface. Rather, they should be added to the {@link Counters} class.
 *
 * @author dramage
 * @author cer
 * @author pado
 */
public interface Counter<E> extends PrettyLoggable {

  /**
   * Returns a factory that can create new instances of this kind of Counter.
   *
   * @return A factory that can create new instances of this kind of Counter.
   */
  public Factory<Counter<E>> getFactory();

  /**
   * Sets the default return value. This value is returned when you get
   * the value for keys that are not in the Counter. It is zero by
   * default, but can sometimes usefully by set to other values like
   * Double.NaN or Double.NEGATIVE_INFINITY.
   *
   * @param rv The default value
   */
  public void setDefaultReturnValue(double rv) ;

  /**
   * Returns the default return value.
   *
   * @return The default return value.
   */
  public double defaultReturnValue() ;

  /**
   * Returns the count for this key as a double. This is the
   * defaultReturnValue (0.0, if it hasn't been set) if the key hasn't
   * previously been seen.
   *
   * @param key The key
   * @return The count
   */
  public double getCount(Object key);

  /**
   * Sets the count for the given key to be the given value.
   * This will replace any existing count for the key.
   * To add to a count instead of replacing it, use
   * {@link #incrementCount(Object,double)}.
   *
   * @param key The key
   * @param value The count
   */
  public void setCount(E key, double value);

  /**
   * Increments the count for the given key by the given value. If the key
   * hasn't been seen before, it is assumed to have count 0.0, and thus this
   * method will set its count to the given amount. <i>Note that this is
   * true regardless of the setting of defaultReturnValue.</i>
   * Negative increments are equivalent to calling <tt>decrementCount</tt>.
   * To more conveniently increment the count by 1.0, use
   * {@link #incrementCount(Object)}.
   * To set a count to a specific value instead of incrementing it, use
   * {@link #setCount(Object,double)}.
   *
   * @param key The key to increment
   * @param value The amount to increment it by
   * @return The value associated with they key, post-increment.
   */
  public double incrementCount(E key, double value);

  /**
   * Increments the count for this key by 1.0. If the key hasn't been seen
   * before, it is assumed to have count 0.0, and thus this method will set
   * its count to 1.0. <i>Note that this is
   * true regardless of the setting of defaultReturnValue.</i>
   * To increment the count by a value other than 1.0, use
   * {@link #incrementCount(Object,double)}.
   * To set a count to a specific value instead of incrementing it, use
   * {@link #setCount(Object,double)}.
   *
   * @param key The key to increment by 1.0
   * @return The value associated with they key, post-increment.
   */
  public double incrementCount(E key);

  /**
   * Decrements the count for this key by the given value.
   * If the key hasn't been seen before, it is assumed to have count 0.0, and
   * thus this  method will set its count to the negative of the given amount.
   * <i>Note that this is true regardless of the setting of defaultReturnValue.</i>
   * Negative increments are equivalent to calling <code>incrementCount</code>.
   * To more conveniently decrement the count by 1.0, use
   * {@link #decrementCount(Object)}.
   * To set a count to a specific value instead of decrementing it, use
   * {@link #setCount(Object,double)}.
   *
   * @param key The key to decrement
   * @param value The amount to decrement it by
   * @return The value associated with they key, post-decrement.
   */
  public double decrementCount(E key, double value);

  /**
   * Decrements the count for this key by 1.0.
   * If the key hasn't been seen before, it is assumed to have count 0.0,
   * and thus this method will set its count to -1.0. <i>Note that this is
   * true regardless of the setting of defaultReturnValue.</i>
   * To decrement the count by a value other than 1.0, use
   * {@link #decrementCount(Object,double)}.
   * To set a count to a specific value instead of decrementing it, use
   * {@link #setCount(Object,double)}.
   *
   * @param key The key to decrement by 1.0
   * @return The value of associated with they key, post-decrement.
   */
  public double decrementCount(E key);

  /**
   * Increments the count stored in log space for this key by the given
   * log-transformed value.
   * If the current count for the key is v1, and you call
   * logIncrementCount with a value of v2, then the new value will
   * be log(e^v1 + e^v2). If the key
   * hasn't been seen before, it is assumed to have count
   * Double.NEGATIVE_INFINITY, and thus this
   * method will set its count to the given amount.  <i>Note that this is
   * true regardless of the setting of defaultReturnValue.</i>
   * To set a count to a specific value instead of incrementing it, you need
   * to first take the log yourself and then to call
   * {@link #setCount(Object,double)}.
   *
   * @param key The key to increment
   * @param value The amount to increment it by, in log space
   * @return The value associated with they key, post-increment, in log space
   */
  public double logIncrementCount(E key, double value);

  /**
   * Adds the counts in the given Counter to the counts in this Counter.
   * This is identical in effect to calling Counters.addInPlace(this, counter).
   *
   * @param counter The Counter whose counts will be added. For each key in
   *   counter, if it is not in this, then it will be added with value
   *   <code>counter.getCount(key)</code>. Otherwise, it will have value
   *   <code>this.getCount(key) + counter.getCount(key)</code>.
   */
  public void addAll(Counter<E> counter);

  /**
   * Removes the given key and its associated value from this Counter.
   * Its count will now be returned as the defaultReturnValue and it
   * will no longer be considered previously seen. If a key not contained in
   * the Counter is given, no action is performed on the Counter and the
   * defaultValue is returned.  This behavior echoes that of HashMap, but differs
   * since a HashMap returns a Double (rather than double) and thus returns null
   * if a key is not present.  Any future revisions of Counter should preserve
   * the ability to "remove" a key that is not present in the Counter.
   *
   * @param key The key
   * @return The value removed from the map or the default value if no
   *   count was associated with that key.
   */
  public double remove(E key);

  /** Returns whether a Counter contains a key.
   *
   *  @param key The key
   *  @return true iff key is a key in this Counter.
   */
  public boolean containsKey(E key);

  /**
   * Returns the Set of keys in this counter.
   *
   * @return The Set of keys in this counter.
   */
  public Set<E> keySet();

  /**
   * Returns a copy of the values currently in this counter.
   * (You should regard this Collection as read-only for forward
   * compatibility; at present implementations differ on how they
   * respond to attempts to change this Collection.)
   *
   * @return A copy of the values currently in this counter.
   */
  public Collection<Double> values();

  /**
   * Returns a view of the entries in this counter.  The values
   * can be safely modified with setValue().
   *
   * @return A view of the entries in this counter
   */
  public Set<Map.Entry<E,Double>> entrySet();

  /**
   * Removes all entries from the counter.
   */
  public void clear();

  /**
   * Returns the number of entries stored in this counter.
   * @return The number of entries in this counter.
   */
  public int size();

  /**
   * Computes the total of all counts in this counter, and returns it
   * as a double.  (Existing implementations cache this value, so that this
   * operation is cheap.)
   *
   * @return The total (arithmetic sum) of all counts in this counter.
   */
  public double totalCount();

}
