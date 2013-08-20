package edu.stanford.nlp.math.mtj;

import it.unimi.dsi.fastutil.ints.Int2DoubleMap;

import java.util.Iterator;
import java.util.Map;

import no.uib.cipr.matrix.AbstractVector;
import no.uib.cipr.matrix.Vector;
import no.uib.cipr.matrix.VectorEntry;
import edu.stanford.nlp.stats.counters.ints.Int2DoubleCounter;
import edu.stanford.nlp.stats.counters.ints.Int2DoubleOpenHashCounter;

/**
 * Sparse Vector implementation backed by an Int2DoubleOpenHashCounter.
 * 
 * @author dramage
 */
public class SparseHashVector extends AbstractVector {

  protected final Int2DoubleCounter map;

  public SparseHashVector(int size) {
    super(size);
    this.map = new Int2DoubleOpenHashCounter();
  }

  public SparseHashVector(int size, Int2DoubleCounter map) {
    super(size);
    this.map = map;
  }

  public SparseHashVector(int size, Map<Integer,? extends Number> map) {
    super(size);
    this.map = new Int2DoubleOpenHashCounter();
    for (Map.Entry<Integer,? extends Number> entry : map.entrySet()) {
      this.map.put(entry.getKey().intValue(), entry.getValue().doubleValue());
    }
  }

  @Override
  public void set(int index, double value) {
    check(index);
    map.put(index, value);
  }

  @Override
  public void add(int index, double value) {
    check(index);
    map.increment(index, value);
  }

  @Override
  public double get(int index) {
    check(index);
    return map.get(index);
  }

  @Override
  public Vector copy() {
    return new SparseHashVector(this.size, new Int2DoubleOpenHashCounter(map));
  }

  /**
   * Returns an iterator over entries in this vector.  Re-uses the returned
   * VectorEntry object for higher performance.
   */
  @Override
  public Iterator<VectorEntry> iterator() {
    return new Iterator<VectorEntry>() {
      Iterator<Map.Entry<Integer, Double>> iterator = map.entrySet().iterator();

      Int2DoubleMap.Entry mapEntry;

      VectorEntry vectorEntry = new VectorEntry() {
        public double get() {
          return mapEntry.getDoubleValue();
        }

        public int index() {
          return mapEntry.getIntKey();
        }

        public void set(double value) {
          mapEntry.setValue(value);
        }
      };

      public boolean hasNext() {
        return iterator.hasNext();
      }

      public VectorEntry next() {
        iterator.next();
        return vectorEntry;
      }

      public void remove() {
        iterator.remove();
      }
    };
  }

}
