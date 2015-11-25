package edu.stanford.nlp.stats;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.MapFactory;
import edu.stanford.nlp.util.MutableInteger;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;


/**
 * A class representing a mapping between pairs of typed objects and int values.
 * (Copied from TwoDimensionalCounter)
 *
 * @author Teg Grenager
 * @author Angel Chang
 */
public class TwoDimensionalIntCounter<K1, K2> implements Serializable {

  private static final long serialVersionUID = 1L;

  // the outermost Map
  private Map<K1, IntCounter<K2>> map;

  // the total of all counts
  private int total;

  // the MapFactory used to make new maps to counters
  private MapFactory<K1,IntCounter<K2>> outerMF;

  // the MapFactory used to make new maps in the inner counter
  private MapFactory<K2, MutableInteger> innerMF;

  private int defaultValue = 0;

  public void defaultReturnValue(double rv) { defaultValue = (int) rv; }

  public void defaultReturnValue(int rv) { defaultValue = rv; }

  public int defaultReturnValue() { return defaultValue; }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof TwoDimensionalIntCounter)) return false;

    return ((TwoDimensionalIntCounter<?,?>) o).map.equals(map);
  }

  @Override
  public int hashCode() {
    return map.hashCode() + 17;
  }

  /**
   * @return the inner Counter associated with key o
   */
  public IntCounter<K2> getCounter(K1 o) {
    IntCounter<K2> c = map.get(o);
    if (c == null) {
      c = new IntCounter<>(innerMF);
      c.setDefaultReturnValue(defaultValue);
      map.put(o, c);
    }
    return c;
  }

  public Set<Map.Entry<K1,IntCounter<K2>>> entrySet(){
    return map.entrySet();
  }

  /**
   * @return total number of entries (key pairs)
   */
  public int size() {
    int result = 0;
    for (K1 o : firstKeySet()) {
      IntCounter<K2> c = map.get(o);
      result += c.size();
    }
    return result;
  }

  public boolean containsKey(K1 o1, K2 o2) {
    if (!map.containsKey(o1)) return false;
    IntCounter<K2> c = map.get(o1);
    return c.containsKey(o2);
  }

  /**
   */
  public void incrementCount(K1 o1, K2 o2) {
    incrementCount(o1, o2, 1);
  }

  /**
   */
  public void incrementCount(K1 o1, K2 o2, double count) {
    incrementCount(o1, o2, (int) count);
  }

  /**
   */
  public void incrementCount(K1 o1, K2 o2, int count) {
    IntCounter<K2> c = getCounter(o1);
    c.incrementCount(o2, count);
    total += count;
  }

  /**
   */
  public void decrementCount(K1 o1, K2 o2) {
    incrementCount(o1, o2, -1);
  }

  /**
   */
  public void decrementCount(K1 o1, K2 o2, double count) {
    incrementCount(o1, o2, -count);
  }

  /**
   */
  public void decrementCount(K1 o1, K2 o2, int count) {
    incrementCount(o1, o2, -count);
  }

  /**
   */
  public void setCount(K1 o1, K2 o2, double count) {
    setCount(o1, o2, (int) count);
  }

  /**
   */
  public void setCount(K1 o1, K2 o2, int count) {
    IntCounter<K2> c = getCounter(o1);
    int oldCount = getCount(o1, o2);
    total -= oldCount;
    c.setCount(o2, count);
    total += count;
  }

  public int remove(K1 o1, K2 o2) {
    IntCounter<K2> c = getCounter(o1);
    int oldCount = getCount(o1, o2);
    total -= oldCount;
    c.remove(o2);
    if (c.isEmpty()) {
      map.remove(o1);
    }
    return oldCount;
  }

  /**
   */
  public int getCount(K1 o1, K2 o2) {
    IntCounter<K2> c = getCounter(o1);
    if (c.totalCount() == 0 && !c.keySet().contains(o2)) { return defaultReturnValue(); }
    return c.getIntCount(o2);
  }

  /**
   * Takes linear time.
   *
   */
  public int totalCount() {
    return total;
  }

  /**
   */
  public int totalCount(K1 k1) {
    IntCounter<K2> c = getCounter(k1);
    return c.totalIntCount();
  }

  public IntCounter<K1> totalCounts() {
    IntCounter<K1> tc = new IntCounter<>();
    for (K1 k1:map.keySet()) {
      tc.setCount(k1, map.get(k1).totalCount());
    }
    return tc;
  }

  public Set<K1> firstKeySet() {
    return map.keySet();
  }

  /**
   * replace the counter for K1-index o by new counter c
   */
  public IntCounter<K2> setCounter(K1 o, IntCounter<K2> c) {
    IntCounter<K2> old = getCounter(o);
    total -= old.totalIntCount();
    map.put(o, c);
    total += c.totalIntCount();
    return old;
  }

  /**
   * Produces a new ConditionalCounter.
   *
   * @return a new ConditionalCounter, where order of indices is reversed
   */
  @SuppressWarnings({"unchecked"})
  public static <K1,K2> TwoDimensionalIntCounter<K2,K1> reverseIndexOrder(TwoDimensionalIntCounter<K1,K2> cc) {
    // the typing on the outerMF is violated a bit, but it'll work....
    TwoDimensionalIntCounter<K2,K1> result = new TwoDimensionalIntCounter<>(
            (MapFactory) cc.outerMF, (MapFactory) cc.innerMF);

    for (K1 key1 : cc.firstKeySet()) {
      IntCounter<K2> c = cc.getCounter(key1);
      for (K2 key2 : c.keySet()) {
        int count = c.getIntCount(key2);
        result.setCount(key2, key1, count);
      }
    }
    return result;
  }

  /**
   * A simple String representation of this TwoDimensionalCounter, which has
   * the String representation of each key pair
   * on a separate line, followed by the count for that pair.
   * The items are tab separated, so the result is a tab-separated value (TSV)
   * file.  Iff none of the keys contain spaces, it will also be possible to
   * treat this as whitespace separated fields.
   */
  @Override
  public String toString() {
    StringBuilder buff = new StringBuilder();
    for (K1 key1 : map.keySet()) {
      IntCounter<K2> c = getCounter(key1);
      for (K2 key2 : c.keySet()) {
        double score = c.getCount(key2);
        buff.append(key1).append("\t").append(key2).append("\t").append(score).append("\n");
      }
    }
    return buff.toString();
  }

  @SuppressWarnings({"unchecked"})
  public String toMatrixString(int cellSize) {
    List<K1> firstKeys = new ArrayList<>(firstKeySet());
    List<K2> secondKeys = new ArrayList<>(secondKeySet());
    Collections.sort((List<? extends Comparable>)firstKeys);
    Collections.sort((List<? extends Comparable>)secondKeys);
    int[][] counts = toMatrix(firstKeys, secondKeys);
    return ArrayMath.toString(counts, firstKeys.toArray(), secondKeys.toArray(), cellSize, cellSize, new DecimalFormat(), true);
  }

  /**
   * Given an ordering of the first (row) and second (column) keys, will produce a double matrix.
   *
   */
  public int[][] toMatrix(List<K1> firstKeys, List<K2> secondKeys) {
    int[][] counts = new int[firstKeys.size()][secondKeys.size()];
    for (int i = 0; i < firstKeys.size(); i++) {
      for (int j = 0; j < secondKeys.size(); j++) {
        counts[i][j] = getCount(firstKeys.get(i), secondKeys.get(j));
      }
    }
    return counts;
  }

  @SuppressWarnings({"unchecked"})
  public String toCSVString(NumberFormat nf) {
    List<K1> firstKeys = new ArrayList<>(firstKeySet());
    List<K2> secondKeys = new ArrayList<>(secondKeySet());
    Collections.sort((List<? extends Comparable>)firstKeys);
    Collections.sort((List<? extends Comparable>)secondKeys);
    StringBuilder b = new StringBuilder();
    String[] headerRow = new String[secondKeys.size() + 1];
    headerRow[0] = "";
    for (int j = 0; j < secondKeys.size(); j++) {
      headerRow[j + 1] = secondKeys.get(j).toString();
    }
    b.append(StringUtils.toCSVString(headerRow)).append("\n");
    for (K1 rowLabel : firstKeys) {
      String[] row = new String[secondKeys.size() + 1];
      row[0] = rowLabel.toString();
      for (int j = 0; j < secondKeys.size(); j++) {
        K2 colLabel = secondKeys.get(j);
        row[j + 1] = nf.format(getCount(rowLabel, colLabel));
      }
      b.append(StringUtils.toCSVString(row)).append("\n");
    }
    return b.toString();
  }

  public static <CK1 extends Comparable<CK1>, CK2 extends Comparable<CK2>> String toCSVString(
          TwoDimensionalIntCounter<CK1, CK2> counter,
          NumberFormat nf, Comparator<CK1> key1Comparator, Comparator<CK2> key2Comparator) {
    List<CK1> firstKeys = new ArrayList<>(counter.firstKeySet());
    List<CK2> secondKeys = new ArrayList<>(counter.secondKeySet());
    Collections.sort(firstKeys, key1Comparator);
    Collections.sort(secondKeys, key2Comparator);
    StringBuilder b = new StringBuilder();
    int secondKeysSize = secondKeys.size();
    String[] headerRow = new String[secondKeysSize + 1];
    headerRow[0] = "";

    for (int j = 0; j < secondKeysSize; j++) {
      headerRow[j + 1] = secondKeys.get(j).toString();
    }
    b.append(StringUtils.toCSVString(headerRow)).append('\n');
    for (CK1 rowLabel : firstKeys) {
      String[] row = new String[secondKeysSize + 1];
      row[0] = rowLabel.toString();
      for (int j = 0; j < secondKeysSize; j++) {
        CK2 colLabel = secondKeys.get(j);
        row[j + 1] = nf.format(counter.getCount(rowLabel, colLabel));
      }
      b.append(StringUtils.toCSVString(row)).append('\n');
    }
    return b.toString();
  }

  public Set<K2> secondKeySet() {
    Set<K2> result = Generics.newHashSet();
    for (K1 k1 : firstKeySet()) {
      for (K2 k2 : getCounter(k1).keySet()) {
        result.add(k2);
      }
    }
    return result;
  }

  public boolean isEmpty() {
    return map.isEmpty();
  }

  public IntCounter<Pair<K1, K2>> flatten() {
    IntCounter<Pair<K1, K2>> result = new IntCounter<>();
    result.setDefaultReturnValue(defaultValue);
    for (K1 key1 : firstKeySet()) {
      IntCounter<K2> inner = getCounter(key1);
      for (K2 key2 : inner.keySet()) {
        result.setCount(new Pair<>(key1, key2), inner.getIntCount(key2));
      }
    }
    return result;
  }

  public void addAll(TwoDimensionalIntCounter<K1, K2> c) {
    for (K1 key : c.firstKeySet()) {
      IntCounter<K2> inner = c.getCounter(key);
      IntCounter<K2> myInner = getCounter(key);
      Counters.addInPlace(myInner, inner);
      total += inner.totalIntCount();
    }
  }

  public void addAll(K1 key, IntCounter<K2> c) {
    IntCounter<K2> myInner = getCounter(key);
    Counters.addInPlace(myInner, c);
    total += c.totalIntCount();
  }

  public void subtractAll(K1 key, IntCounter<K2> c) {
    IntCounter<K2> myInner = getCounter(key);
    Counters.subtractInPlace(myInner, c);
    total -= c.totalIntCount();
  }



  public void subtractAll(TwoDimensionalIntCounter<K1, K2> c, boolean removeKeys) {
    for (K1 key : c.firstKeySet()) {
      IntCounter<K2> inner = c.getCounter(key);
      IntCounter<K2> myInner = getCounter(key);
      Counters.subtractInPlace(myInner, inner);
      if (removeKeys) {
        Counters.retainNonZeros(myInner);
      }
      total -= inner.totalIntCount();
    }
  }

  public void removeZeroCounts() {
    Set<K1> firstKeySet = Generics.newHashSet(firstKeySet());
    for (K1 k1 : firstKeySet) {
      IntCounter<K2> c = getCounter(k1);
      Counters.retainNonZeros(c);
      if (c.isEmpty()) {
        map.remove(k1); // it's empty, get rid of it!
      }
    }
  }

  public void remove(K1 key) {
    IntCounter<K2> counter = map.get(key);
    if (counter != null) { total -= counter.totalIntCount(); }
    map.remove(key);
  }

  public void clean() {
    for (K1 key1 : Generics.newHashSet(map.keySet())) {
      IntCounter<K2> c = map.get(key1);
      for (K2 key2 : Generics.newHashSet(c.keySet())) {
        if (c.getIntCount(key2) == 0) {
          c.remove(key2);
        }
      }
      if (c.keySet().isEmpty()) {
        map.remove(key1);
      }
    }
  }

  public MapFactory<K1,IntCounter<K2>> getOuterMapFactory() {
    return outerMF;
  }

  public MapFactory<K2,MutableInteger> getInnerMapFactory() {
    return innerMF;
  }

  public TwoDimensionalIntCounter() {
    this(MapFactory.<K1,IntCounter<K2>>hashMapFactory(), MapFactory.<K2,MutableInteger>hashMapFactory());
  }

  public TwoDimensionalIntCounter(int initialCapacity) {
    this(MapFactory.<K1,IntCounter<K2>>hashMapFactory(), MapFactory.<K2,MutableInteger>hashMapFactory(), initialCapacity);
  }

  public TwoDimensionalIntCounter(MapFactory<K1,IntCounter<K2>> outerFactory, MapFactory<K2,MutableInteger> innerFactory) {
    this(outerFactory, innerFactory, 100);
  }

  public TwoDimensionalIntCounter(MapFactory<K1,IntCounter<K2>> outerFactory, MapFactory<K2,MutableInteger> innerFactory, int initialCapacity) {
    innerMF = innerFactory;
    outerMF = outerFactory;
    map = outerFactory.newMap(initialCapacity);
    total = 0;
  }

}
