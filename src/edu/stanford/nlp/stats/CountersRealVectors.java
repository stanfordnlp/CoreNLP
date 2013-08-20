package edu.stanford.nlp.stats;

import java.util.Iterator;
import java.util.Map;

import edu.stanford.nlp.util.Index;
import org.apache.commons.math.linear.ArrayRealVector;
import org.apache.commons.math.linear.OpenMapRealVector;
import org.apache.commons.math.linear.RealVector;

/** A class for translating between JavaNLP Counter and Apache
 *  commons math RealVector.
 *
 *  @author Dan Cer 
 */
public class CountersRealVectors {

  private CountersRealVectors() {
  }


  /**
   * Convert a Counter to an Apache commons math RealVector.
   *
   * @param counter
   * @param keyIndex
   */
  public static <E> RealVector toSparseRealVector(Counter<E> counter, Index<E> keyIndex) {
    return toSparseRealVector(counter, keyIndex, keyIndex.size());
  }


  public static <E> RealVector toSparseRealVector(Counter<E> counter, Index<E> keyIndex, int size) {
    RealVector r = new OpenMapRealVector(size, counter.size());
    for (Map.Entry<E, Double> entry : counter.entrySet()) {
      int index = keyIndex.indexOf(entry.getKey());
      if (index == -1) {
        throw new RuntimeException(String.format("Counter key '%s' not in keyIndex", entry.getKey()));
      }
      r.setEntry(index, entry.getValue());
    }
    return r;
  }


  public static <E> RealVector toRealVector(Counter<E> counter, Index<E> keyIndex, int size) {
    RealVector r = new ArrayRealVector(size);
    for (Map.Entry<E, Double> entry : counter.entrySet()) {
      int index = keyIndex.indexOf(entry.getKey());
      if (index == -1) {
        throw new RuntimeException(String.format("Counter key '%s' not in keyIndex", entry.getKey()));
      }
      r.setEntry(index, entry.getValue());
    }
    return r;
  }


  public static <E> RealVector toRealVector(Counter<E> counter, Index<E> keyIndex) {
    return toRealVector(counter, keyIndex, keyIndex.size());
  }


  public static <E> Counter<E> fromRealVector(RealVector r, Index<E> keyIndex) {
    Counter<E> counter = new ClassicCounter<E>();
    if (r instanceof ArrayRealVector) {
      ArrayRealVector ar = (ArrayRealVector) r;
      double[] vals = ar.getDataRef();
      int entries = keyIndex.size();
      for (int i = 0; i < entries; i++) {
        counter.setCount(keyIndex.get(i), vals[i]);
      }
    } else {

      for (Iterator<RealVector.Entry> itr = r.sparseIterator(); itr.hasNext();) {
        RealVector.Entry entry = itr.next();
        double v = entry.getValue();
        if (v != 0) {
          counter.setCount(keyIndex.get(entry.getIndex()), v);
        }
      }
    }

    return counter;
  }

}