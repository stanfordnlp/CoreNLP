package edu.stanford.nlp.stats;

import edu.stanford.nlp.util.Pair;

import java.text.NumberFormat;
import java.util.*;

/**
 * Interface representing a mapping between pairs of typed objects and double
 * values.
 * 
 * @author Angel Chang
 */
public interface TwoDimensionalCounterInterface<K1, K2>  {

  public void defaultReturnValue(double rv);
  public double defaultReturnValue();

  /**
   * @return total number of entries (key pairs)
   */
  public int size();

  public boolean containsKey(K1 o1, K2 o2);

  /**
   */
  public void incrementCount(K1 o1, K2 o2);

  /**
   */
  public void incrementCount(K1 o1, K2 o2, double count);

  /**
   */
  public void decrementCount(K1 o1, K2 o2);

  /**
   */
  public void decrementCount(K1 o1, K2 o2, double count);

  /**
   */
  public void setCount(K1 o1, K2 o2, double count);

  public double remove(K1 o1, K2 o2);

  /**
   */
  public double getCount(K1 o1, K2 o2);

  public double totalCount();

  /**
   */
  public double totalCount(K1 k1);

  public Set<K1> firstKeySet();

  public Set<K2> secondKeySet();

  public boolean isEmpty();

  public void remove(K1 key);
  
  public String toMatrixString(int cellSize);

  /**
   * Given an ordering of the first (row) and second (column) keys, will produce
   * a double matrix.
   */
  public double[][] toMatrix(List<K1> firstKeys, List<K2> secondKeys);

  public String toCSVString(NumberFormat nf);


  /** Counter based operations */

  /**
   * @return the inner Counter associated with key o
   */
  public Counter<K2> getCounter(K1 o);

  //public Set<Map.Entry<K1, ClassicCounter<K2>>> entrySet();

  /**
   * replace the counter for K1-index o by new counter c
   */
  //public Counter<K2> setCounter(K1 o, Counter<K2> c);

  //public Counter<Pair<K1, K2>> flatten();

  //public void addAll(TwoDimensionalCounterInterface<K1, K2> c);

  //public void addAll(K1 key, Counter<K2> c);

  //public void subtractAll(K1 key, Counter<K2> c);

  //public void subtractAll(TwoDimensionalCounterInterface<K1, K2> c, boolean removeKeys);

  /**
   * Returns the counters with keys as the first key and count as the
   * total count of the inner counter for that key
   *
   * @return counter of type K1
   */
  //public Counter<K1> sumInnerCounter();

}
