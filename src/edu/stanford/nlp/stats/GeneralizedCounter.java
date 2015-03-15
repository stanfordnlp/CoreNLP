package edu.stanford.nlp.stats;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.MutableDouble;

/**
 * A class for keeping double counts of {@link List}s of a
 * prespecified length.  A depth <i>n</i> GeneralizedCounter can be
 * thought of as a conditionalized count over <i>n</i> classes of
 * objects, in a prespecified order.  Also offers a read-only view as
 * a Counter.  <p> This class is serializable but no guarantees are
 * made about compatibility version to version.
 *
 * <p/>
 * This is the worst class. Use TwoDimensionalCounter. If you need a third,
 * write ThreeDimensionalCounter, but don't use this.
 *
 * @author Roger Levy
 */
public class GeneralizedCounter<K> implements Serializable {

  private static final long serialVersionUID = 1;

  private static final Object[] zeroKey = new Object[0];

  private Map<K,Object> map = new HashMap<K, Object>();

  private int depth;
  private double total;


  /**
   * GeneralizedCounter must be constructed with a depth parameter
   */
  private GeneralizedCounter() {
  }

  /**
   * Constructs a new GeneralizedCounter of a specified depth
   *
   * @param depth the depth of the GeneralizedCounter
   */
  public GeneralizedCounter(int depth) {
    this.depth = depth;
  }

  /**
   * Returns the set of entries in the GeneralizedCounter.
   * Here, each key is a read-only {@link
   * List} of size equal to the depth of the GeneralizedCounter, and
   * each value is a {@link Double}.  Each entry is a {@link java.util.Map.Entry} object,
   * but these objects
   * do not support the {@link java.util.Map.Entry#setValue} method; attempts to call
   * that method with result
   * in an {@link UnsupportedOperationException} being thrown.
   */
  public Set<Map.Entry<List<K>,Double>> entrySet() {
    return ErasureUtils.<Set<Map.Entry<List<K>,Double>>>uncheckedCast(entrySet(new HashSet<Map.Entry<Object,Double>>(), zeroKey, true));
  }

  /* this is (non-tail) recursive right now, haven't figured out a way
  * to speed it up */
  private Set<Map.Entry<Object,Double>> entrySet(Set<Map.Entry<Object,Double>> s, Object[] key, boolean useLists) {
    if (depth == 1) {
      //System.out.println("key is long enough to add to set");
      Set<K> keys = map.keySet();
      for (K finalKey: keys) {
        // array doesn't escape
        K[] newKey = ErasureUtils.<K>mkTArray(Object.class,key.length + 1);
        if (key.length > 0) {
          System.arraycopy(key, 0, newKey, 0, key.length);
        }
        newKey[key.length] = finalKey;
        MutableDouble value = (MutableDouble) map.get(finalKey);
        Double value1 = new Double(value.doubleValue());
        if (useLists) {
          s.add(new Entry<Object,Double>(Arrays.asList(newKey), value1));
        } else {
          s.add(new Entry<Object,Double>(newKey[0], value1));
        }

      }
    } else {
      Set<K> keys = map.keySet();
      //System.out.println("key length " + key.length);
      //System.out.println("keyset level " + depth + " " + keys);
      for (K o: keys) {
        Object[] newKey = new Object[key.length + 1];
        if (key.length > 0) {
          System.arraycopy(key, 0, newKey, 0, key.length);
        }
        newKey[key.length] = o;
        //System.out.println("level " + key.length + " current key " + Arrays.asList(newKey));
        conditionalizeHelper(o).entrySet(s, newKey, true);
      }
    }
    //System.out.println("leaving key length " + key.length);
    return s;
  }

  /**
   * Returns a set of entries, where each key is a read-only {@link
   * List} of size one less than the depth of the GeneralizedCounter, and
   * each value is a {@link ClassicCounter}.  Each entry is a {@link java.util.Map.Entry} object, but these objects
   * do not support the {@link java.util.Map.Entry#setValue} method; attempts to call that method with result
   * in an {@link UnsupportedOperationException} being thrown.
   */
  public Set<Map.Entry<List<K>, ClassicCounter<K>>> lowestLevelCounterEntrySet() {
    return ErasureUtils.<Set<Map.Entry<List<K>, ClassicCounter<K>>>>uncheckedCast(lowestLevelCounterEntrySet(new HashSet<Map.Entry<Object, ClassicCounter<K>>>(), zeroKey, true));
  }

  /* this is (non-tail) recursive right now, haven't figured out a way
  * to speed it up */
  private Set<Map.Entry<Object, ClassicCounter<K>>> lowestLevelCounterEntrySet(Set<Map.Entry<Object, ClassicCounter<K>>> s, Object[] key, boolean useLists) {
    Set<K> keys = map.keySet();
    if (depth == 2) {
      // add these counters to set
      for (K finalKey: keys) {
        K[] newKey = ErasureUtils.<K>mkTArray(Object.class,key.length + 1);
        if (key.length > 0) {
          System.arraycopy(key, 0, newKey, 0, key.length);
        }
        newKey[key.length] = finalKey;
        ClassicCounter<K> c = conditionalizeHelper(finalKey).oneDimensionalCounterView();
        if (useLists) {
          s.add(new Entry<Object,ClassicCounter<K>>(Arrays.asList(newKey), c));
        } else {
          s.add(new Entry<Object,ClassicCounter<K>>(newKey[0], c));
        }
      }
    } else {
      //System.out.println("key length " + key.length);
      //System.out.println("keyset level " + depth + " " + keys);
      for (K o: keys) {
        Object[] newKey = new Object[key.length + 1];
        if (key.length > 0) {
          System.arraycopy(key, 0, newKey, 0, key.length);
        }
        newKey[key.length] = o;
        //System.out.println("level " + key.length + " current key " + Arrays.asList(newKey));
        conditionalizeHelper(o).lowestLevelCounterEntrySet(s, newKey, true);
      }
    }
    //System.out.println("leaving key length " + key.length);
    return s;
  }

  private static class Entry<K, V> implements Map.Entry<K, V> {
    private K key;
    private V value;

    Entry(K key, V value) {
      this.key = key;
      this.value = value;
    }

    public K getKey() {
      return key;
    }

    public V getValue() {
      return value;
    }

    public V setValue(V value) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Entry)) {
        return false;
      }
      Entry<K,V> e = ErasureUtils.<Entry<K,V>>uncheckedCast(o);

      Object key1 = e.getKey();
      if (!(key != null && key.equals(key1))) {
        return false;
      }

      Object value1 = e.getValue();
      if (!(value != null && value.equals(value1))) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      if (key == null || value == null) {
        return 0;
      }
      return key.hashCode() ^ value.hashCode();
    }

    @Override
    public String toString() {
      return key.toString() + "=" + value.toString();
    }

  } // end static class Entry


  /**
   * returns the total count of objects in the GeneralizedCounter.
   */
  public double totalCount() {
    if (depth() == 1) {
      return total; // I think this one is always OK.  Not very principled here, though.
    } else {
      double result = 0.0;
      for (K o: topLevelKeySet()) {
        result += conditionalizeOnce(o).totalCount();
      }
      return result;
    }
  }

  /**
   * Returns the set of elements that occur in the 0th position of a
   * {@link List} key in the GeneralizedCounter.
   *
   * @see #conditionalize(List)
   * @see #getCount
   */
  public Set<K> topLevelKeySet() {
    return map.keySet();
  }

  /**
   * Returns the set of keys, as read-only {@link List}s of size
   * equal to the depth of the GeneralizedCounter.
   */
  public Set<List<K>> keySet() {
    return ErasureUtils.<Set<List<K>>>uncheckedCast(keySet(new HashSet<Object>(), zeroKey, true));
  }

  /* this is (non-tail) recursive right now, haven't figured out a way
  * to speed it up */
  private Set<Object> keySet(Set<Object> s, Object[] key, boolean useList) {
    if (depth == 1) {
      //System.out.println("key is long enough to add to set");
      Set<K> keys = map.keySet();
      for (Object oldKey: keys) {
        Object[] newKey = new Object[key.length + 1];
        if (key.length > 0) {
          System.arraycopy(key, 0, newKey, 0, key.length);
        }
        newKey[key.length] = oldKey;
        if (useList) {
          s.add(Arrays.asList(newKey));
        } else {
          s.add(newKey[0]);
        }
      }
    } else {
      Set<K> keys = map.keySet();
      //System.out.println("key length " + key.length);
      //System.out.println("keyset level " + depth + " " + keys);
      for (K o: keys) {
        Object[] newKey = new Object[key.length + 1];
        if (key.length > 0) {
          System.arraycopy(key, 0, newKey, 0, key.length);
        }
        newKey[key.length] = o;
        //System.out.println("level " + key.length + " current key " + Arrays.asList(newKey));
        conditionalizeHelper(o).keySet(s, newKey, true);
      }
    }
    //System.out.println("leaving key length " + key.length);
    return s;
  }

  /**
   * Returns the depth of the GeneralizedCounter (i.e., the dimension
   * of the distribution).
   */
  public int depth() {
    return depth;
  }

  /**
   * Returns true if nothing has a count.
   */
  public boolean isEmpty() {
    return map.isEmpty();
  }


  /**
   * Equivalent to <code>{@link #getCounts}({o})</code>; works only
   * for depth 1 GeneralizedCounters
   */
  public double getCount(Object o) {
    if (depth > 1) {
      wrongDepth();
    }
    Number count = (Number) map.get(o);
    if (count != null) {
      return count.doubleValue();
    } else {
      return 0.0;
    }
  }

  /**
   * A convenience method equivalent to <code>{@link
   * #getCounts}({o1,o2})</code>; works only for depth 2
   * GeneralizedCounters
   */
  public double getCount(K o1, K o2) {
    if (depth != 2) {
      wrongDepth();
    }
    GeneralizedCounter<K> gc1 = ErasureUtils.<GeneralizedCounter<K>>uncheckedCast(map.get(o1));
    if (gc1 == null) {
      return 0.0;
    } else {
      return gc1.getCount(o2);
    }
  }

  /**
   * A convenience method equivalent to <code>{@link
   * #getCounts}({o1,o2,o3})</code>; works only for depth 3
   * GeneralizedCounters
   */
  public double getCount(K o1, K o2, K o3) {
    if (depth != 3) {
      wrongDepth();
    }
    GeneralizedCounter<K> gc1 = ErasureUtils.<GeneralizedCounter<K>>uncheckedCast(map.get(o1));
    if (gc1 == null) {
      return 0.0;
    } else {
      return gc1.getCount(o2, o3);
    }
  }


  /**
   * returns a <code>double[]</code> array of length
   * <code>depth+1</code>, containing the conditional counts on a
   * <code>depth</code>-length list given each level of conditional
   * distribution from 0 to <code>depth</code>.
   */
  public double[] getCounts(List<K> l) {
    if (l.size() != depth) {
      wrongDepth(); //throws exception
    }

    double[] counts = new double[depth + 1];

    GeneralizedCounter<K> next = this;
    counts[0] = next.totalCount();
    Iterator<K> i = l.iterator();
    int j = 1;
    K o = i.next();
    while (i.hasNext()) {
      next = next.conditionalizeHelper(o);
      counts[j] = next.totalCount();
      o = i.next();
      j++;
    }
    counts[depth] = next.getCount(o);

    return counts;
  }

  /* haven't decided about access for this one yet */
  private GeneralizedCounter<K> conditionalizeHelper(K o) {
    if (depth > 1) {
      GeneralizedCounter<K> next = ErasureUtils.<GeneralizedCounter<K>>uncheckedCast(map.get(o));
      if (next == null) // adds a new GeneralizedCounter if needed
      {
        map.put(o, (next = new GeneralizedCounter<K>(depth - 1)));
      }
      return next;
    } else {
      throw new RuntimeException("Error -- can't conditionalize a distribution of depth 1");
    }
  }

  /**
   * returns a GeneralizedCounter conditioned on the objects in the
   * {@link List} argument. The length of the argument {@link List}
   * must be less than the depth of the GeneralizedCounter.
   */
  public GeneralizedCounter<K> conditionalize(List<K> l) {
    int n = l.size();
    if (n >= depth()) {
      throw new RuntimeException("Error -- attempted to conditionalize a GeneralizedCounter of depth " + depth() + " on a vector of length " + n);
    } else {
      GeneralizedCounter<K> next = this;
      for (K o: l) {
        next = next.conditionalizeHelper(o);
      }
      return next;
    }
  }

  /**
   * Returns a GeneralizedCounter conditioned on the given top level object.
   * This is just shorthand (and more efficient) for <code>conditionalize(new Object[] { o })</code>.
   */
  public GeneralizedCounter<K> conditionalizeOnce(K o) {
    if (depth() < 1) {
      throw new RuntimeException("Error -- attempted to conditionalize a GeneralizedCounter of depth " + depth());
    } else {
      return conditionalizeHelper(o);
    }
  }

  /**
   * equivalent to incrementCount(l,o,1.0).
   */
  public void incrementCount(List<K> l, K o) {
    incrementCount(l, o, 1.0);
  }

  /**
   * same as incrementCount(List, double) but as if Object o were at the end of the list
   */
  public void incrementCount(List<K> l, K o, double count) {
    if (l.size() != depth - 1) {
      wrongDepth();
    }

    GeneralizedCounter<K> next = this;
    for (K o2: l) {
      next.addToTotal(count);
      next = next.conditionalizeHelper(o2);
    }
    next.addToTotal(count);

    next.incrementCount1D(o, count);
  }


  /**
   * Equivalent to incrementCount(l, 1.0).
   */
  public void incrementCount(List<K> l) {
    incrementCount(l, 1.0);
  }

  /**
   * adds to count for the {@link #depth()}-dimensional key <code>l</code>.
   */
  public void incrementCount(List<K> l, double count) {
    if (l.size() != depth) {
      wrongDepth(); //throws exception
    }

    GeneralizedCounter<K> next = this;
    Iterator<K> i = l.iterator();
    K o = i.next();
    while (i.hasNext()) {
      next.addToTotal(count);
      next = next.conditionalizeHelper(o);
      o = i.next();
    }
    next.incrementCount1D(o, count);
  }

  /**
   * Equivalent to incrementCount2D(first,second,1.0).
   */
  public void incrementCount2D(K first, K second) {
    incrementCount2D(first, second, 1.0);
  }

  /**
   * Equivalent to incrementCount( new Object[] { first, second }, count ).
   * Makes the special case easier, and also more efficient.
   */
  public void incrementCount2D(K first, K second, double count) {
    if (depth != 2) {
      wrongDepth(); //throws exception
    }

    this.addToTotal(count);
    GeneralizedCounter<K> next = this.conditionalizeHelper(first);
    next.incrementCount1D(second, count);
  }

  /**
   * Equivalent to incrementCount3D(first,second,1.0).
   */
  public void incrementCount3D(K first, K second, K third) {
    incrementCount3D(first, second, third, 1.0);
  }

  /**
   * Equivalent to incrementCount( new Object[] { first, second, third }, count ).
   * Makes the special case easier, and also more efficient.
   */
  public void incrementCount3D(K first, K second, K third, double count) {
    if (depth != 3) {
      wrongDepth(); //throws exception
    }

    this.addToTotal(count);
    GeneralizedCounter<K> next = this.conditionalizeHelper(first);
    next.incrementCount2D(second, third, count);
  }

  private void addToTotal(double d) {
    total += d;
  }

  // for more efficient memory usage
  private transient MutableDouble tempMDouble = null;


  /**
   * Equivalent to incrementCount1D(o, 1.0).
   */
  public void incrementCount1D(K o) {
    incrementCount1D(o, 1.0);
  }

  /**
   * Equivalent to <code>{@link #incrementCount}({o}, count)</code>;
   * only works for a depth 1 GeneralizedCounter.
   */
  public void incrementCount1D(K o, double count) {
    if (depth > 1) {
      wrongDepth();
    }

    addToTotal(count);

    if (tempMDouble == null) {
      tempMDouble = new MutableDouble();
    }
    tempMDouble.set(count);
    MutableDouble oldMDouble = (MutableDouble) map.put(o, tempMDouble);

    if (oldMDouble != null) {
      tempMDouble.set(count + oldMDouble.doubleValue());
    }

    tempMDouble = oldMDouble;

  }

  /**
   * Like {@link ClassicCounter}, this currently returns true if the count is
   * explicitly 0.0 for something
   */
  public boolean containsKey(List<K> key) {
    //     if(! (key instanceof Object[]))
    //       return false;
    //    Object[] o = (Object[]) key;
    GeneralizedCounter<K> next = this;
    for (int i=0; i<key.size()-1; i++) {
      next = next.conditionalizeHelper(key.get(i));
      if (next==null) return false;
    }
    return next.map.containsKey(key.get(key.size()-1));
  }

  public GeneralizedCounter<K> reverseKeys() {
    GeneralizedCounter<K> result = new GeneralizedCounter<K>();
    Set<Map.Entry<List<K>,Double>> entries = entrySet();
    for (Map.Entry<List<K>,Double> entry: entries) {
      List<K> list = entry.getKey();
      double count = entry.getValue();
      Collections.reverse(list);
      result.incrementCount(list, count);
    }
    return result;
  }


  private void wrongDepth() {
    throw new RuntimeException("Error -- attempt to operate with key of wrong length. depth=" + depth);
  }


  /**
   * Returns a read-only synchronous view (not a snapshot) of
   * <code>this</code> as a {@link ClassicCounter}.  Any calls to
   * count-changing or entry-removing operations will result in an
   * {@link UnsupportedOperationException}.  At some point in the
   * future, this view may gain limited writable functionality.
   */
  public ClassicCounter<List<K>> counterView() {
    return new CounterView();
  }

  private class CounterView extends ClassicCounter<List<K>> {

    /**
     *
     */
    private static final long serialVersionUID = -1241712543674668918L;

    @Override
    public double incrementCount(List<K> o, double count) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setCount(List<K> o, double count) {
      throw new UnsupportedOperationException();
    }

    @Override
    public double totalCount() {
      return GeneralizedCounter.this.totalCount();
    }

    @Override
    public double getCount(Object o) {
      List<K> l = (List<K>)o;
      if (l.size() != depth) {
        return 0.0;
      } else {
        return GeneralizedCounter.this.getCounts(l)[depth];
      }
    }

    @Override
    public int size() {
      return GeneralizedCounter.this.map.size();
    }

    @Override
    public Set<List<K>> keySet() {
      return GeneralizedCounter.this.keySet();
    }

    @Override
    public double remove(List<K> o) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsKey(List<K> key) {
      return GeneralizedCounter.this.containsKey(key);
    }

    @Override
    public void clear() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty() {
      return GeneralizedCounter.this.isEmpty();
    }

    @Override
    public Set<Map.Entry<List<K>, Double>> entrySet() {
      return GeneralizedCounter.this.entrySet();
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) {
        return true;
      }
      //return false;
      if (!(o instanceof ClassicCounter)) {
        return false;
      } else {
        // System.out.println("it's a counter!");
        // Set e = entrySet();
        // Set e1 = ((Counter) o).entrySet();
        // System.out.println(e + "\n" + e1);
        return entrySet().equals(((ClassicCounter<?>) o).entrySet());
      }
    }

    @Override
    public int hashCode() {
      int total = 17;
      for (Object o: entrySet()) {
        total = 37 * total + o.hashCode();
      }
      return total;
    }

    @Override
    public String toString() {
      StringBuffer sb = new StringBuffer("{");
      for (Iterator<Map.Entry<List<K>, Double>> i = entrySet().iterator(); i.hasNext();) {
        Map.Entry<List<K>, Double> e = i.next();
        sb.append(e.toString());
        if (i.hasNext()) {
          sb.append(",");
        }
      }
      sb.append("}");
      return sb.toString();
    }

  } // end class CounterView


  /**
   * Returns a read-only synchronous view (not a snapshot) of
   * <code>this</code> as a {@link ClassicCounter}.  Works only with one-dimensional
   * GeneralizedCounters.  Exactly like {@link #counterView}, except
   * that {@link #getCount} operates on primitive objects of the counter instead
   * of singleton lists.  Any calls to
   * count-changing or entry-removing operations will result in an
   * {@link UnsupportedOperationException}.  At some point in the
   * future, this view may gain limited writable functionality.
   */
  public ClassicCounter<K> oneDimensionalCounterView() {
    if (depth != 1) {
      throw new UnsupportedOperationException();
    }
    return new OneDimensionalCounterView();
  }

  private class OneDimensionalCounterView extends ClassicCounter<K> {

    /**
     *
     */
    private static final long serialVersionUID = 5628505169749516972L;

    @Override
    public double incrementCount(K o, double count) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setCount(K o, double count) {
      throw new UnsupportedOperationException();
    }

    @Override
    public double totalCount() {
      return GeneralizedCounter.this.totalCount();
    }

    @Override
    public double getCount(Object o) {
      return GeneralizedCounter.this.getCount(o);
    }

    @Override
    public int size() {
      return GeneralizedCounter.this.map.size();
    }

    @Override
    public Set<K> keySet() {
      return ErasureUtils.<Set<K>>uncheckedCast(GeneralizedCounter.this.keySet(new HashSet<Object>(), zeroKey, false));
    }

    @Override
    public double remove(Object o) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsKey(Object key) {
      return GeneralizedCounter.this.map.containsKey(key);
    }

    @Override
    public void clear() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty() {
      return GeneralizedCounter.this.isEmpty();
    }

    @Override
    public Set<Map.Entry<K, Double>> entrySet() {
      return ErasureUtils.<Set<Map.Entry<K, Double>>>uncheckedCast(GeneralizedCounter.this.entrySet(new HashSet<Map.Entry<Object, Double>>(), zeroKey, false));
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) {
        return true;
      }
      //return false;
      if (!(o instanceof ClassicCounter)) {
        return false;
      } else {
        // System.out.println("it's a counter!");
        // Set e = entrySet();
        // Set e1 = ((Counter) o).map.entrySet();
        // System.out.println(e + "\n" + e1);
        return entrySet().equals(((ClassicCounter<?>) o).entrySet());
      }
    }

    @Override
    public int hashCode() {
      int total = 17;
      for (Object o: entrySet()) {
        total = 37 * total + o.hashCode();
      }
      return total;
    }

    @Override
    public String toString() {
      StringBuffer sb = new StringBuffer("{");
      for (Iterator<Map.Entry<K, Double>> i = entrySet().iterator(); i.hasNext();) {
        Map.Entry<K, Double> e = i.next();
        sb.append(e.toString());
        if (i.hasNext()) {
          sb.append(",");
        }
      }
      sb.append("}");
      return sb.toString();
    }

  } // end class OneDimensionalCounterView


  @Override
  public String toString() {
    return map.toString();
  }

  public String toString(String param) {
    if (param.equals("contingency")) {
      StringBuffer sb = new StringBuffer();
      for (K obj: ErasureUtils.sortedIfPossible(topLevelKeySet())) {
        sb.append(obj);
        sb.append(" = ");
        GeneralizedCounter<K> gc = conditionalizeOnce(obj);
        sb.append(gc);
        sb.append("\n");
      }
      return sb.toString();
    } else if (param.equals("sorted")) {
      StringBuffer sb = new StringBuffer();
      sb.append("{\n");
      for (K obj: ErasureUtils.sortedIfPossible(topLevelKeySet())) {
        sb.append(obj);
        sb.append(" = ");
        GeneralizedCounter<K> gc = conditionalizeOnce(obj);
        sb.append(gc);
        sb.append("\n");
      }
      sb.append("}\n");
      return sb.toString();
    } else {
      return toString();
    }
  }


  /**
   * for testing purposes only
   */
  public static void main(String[] args) {

    Object[] a1 = new Object[]{"a", "b"};
    Object[] a2 = new Object[]{"a", "b"};

    System.out.println(a1.equals(a2));


    GeneralizedCounter<String> gc = new GeneralizedCounter<String>(3);
    gc.incrementCount(Arrays.asList(new String[]{"a", "j", "x"}), 3.0);
    gc.incrementCount(Arrays.asList(new String[]{"a", "l", "x"}), 3.0);
    gc.incrementCount(Arrays.asList(new String[]{"b", "k", "y"}), 3.0);
    gc.incrementCount(Arrays.asList(new String[]{"b", "k", "z"}), 3.0);

    System.out.println("incremented counts.");

    System.out.println(gc.dumpKeys());

    System.out.println("string representation of generalized counter:");
    System.out.println(gc.toString());


    gc.printKeySet();

    System.out.println("entry set:\n" + gc.entrySet());


    arrayPrintDouble(gc.getCounts(Arrays.asList(new String[]{"a", "j", "x"})));
    arrayPrintDouble(gc.getCounts(Arrays.asList(new String[]{"a", "j", "z"})));
    arrayPrintDouble(gc.getCounts(Arrays.asList(new String[]{"b", "k", "w"})));
    arrayPrintDouble(gc.getCounts(Arrays.asList(new String[]{"b", "k", "z"})));

    GeneralizedCounter<String> gc1 = gc.conditionalize(Arrays.asList(new String[]{"a"}));
    gc1.incrementCount(Arrays.asList(new String[]{"j", "x"}));
    gc1.incrementCount2D("j", "z");
    GeneralizedCounter<String> gc2 = gc1.conditionalize(Arrays.asList(new String[]{"j"}));
    gc2.incrementCount1D("x");
    System.out.println("Pretty-printing gc after incrementing gc1:");
    gc.prettyPrint();
    System.out.println("Total: " + gc.totalCount());

    gc1.printKeySet();
    System.out.println("another entry set:\n" + gc1.entrySet());


    ClassicCounter<List<String>> c = gc.counterView();

    System.out.println("string representation of counter view:");
    System.out.println(c.toString());

    double d1 = c.getCount(Arrays.asList(new String[]{"a", "j", "x"}));
    double d2 = c.getCount(Arrays.asList(new String[]{"a", "j", "w"}));

    System.out.println(d1 + " " + d2);


    ClassicCounter<List<String>> c1 = gc1.counterView();

    System.out.println("Count of {j,x} -- should be 3.0\t" + c1.getCount(Arrays.asList(new String[]{"j", "x"})));


    System.out.println(c.keySet() + " size " + c.keySet().size());
    System.out.println(c1.keySet() + " size " + c1.keySet().size());

    System.out.println(c1.equals(c));
    System.out.println(c.equals(c1));
    System.out.println(c.equals(c));

    System.out.println("### testing equality of regular Counter...");

    ClassicCounter<String> z1 = new ClassicCounter<String>();
    ClassicCounter<String> z2 = new ClassicCounter<String>();

    z1.incrementCount("a1");
    z1.incrementCount("a2");

    z2.incrementCount("b");

    System.out.println(z1.equals(z2));

    System.out.println(z1.toString());
    System.out.println(z1.keySet().toString());


  }


  // below is testing code

  private void printKeySet() {
    Set<?> keys = keySet();
    System.out.println("printing keyset:");
    for (Object o: keys) {
      //System.out.println(Arrays.asList((Object[]) i.next()));
      System.out.println(o);
    }
  }


  private static void arrayPrintDouble(double[] o) {
    for (int i = 0, n = o.length; i < n; i++) {
      System.out.print(o[i] + "\t");
    }
    System.out.println();
  }

  private Set<?> dumpKeys() {
    return map.keySet();
  }

  /**
   * pretty-prints the GeneralizedCounter to {@link System#out}.
   */
  public void prettyPrint() {
    prettyPrint(new PrintWriter(System.out, true));
  }

  /**
   * pretty-prints the GeneralizedCounter, using a buffer increment of two spaces.
   */
  public void prettyPrint(PrintWriter pw) {
    prettyPrint(pw, "  ");
  }

  /**
   * pretty-prints the GeneralizedCounter.
   */
  public void prettyPrint(PrintWriter pw, String bufferIncrement) {
    prettyPrint(pw, "", bufferIncrement);
  }

  private void prettyPrint(PrintWriter pw, String buffer, String bufferIncrement) {
    if (depth == 1) {
      for (Map.Entry<?, Double> e: entrySet()) {
        Object key = e.getKey();
        double count = e.getValue();
        pw.println(buffer + key + "\t" + count);
      }
    } else {
      for (K key: topLevelKeySet()) {
        GeneralizedCounter<K> gc1 = conditionalize(Arrays.asList(ErasureUtils.<K[]>uncheckedCast(new Object[]{key})));
        pw.println(buffer + key + "\t" + gc1.totalCount());
        gc1.prettyPrint(pw, buffer + bufferIncrement, bufferIncrement);
      }
    }
  }

}
