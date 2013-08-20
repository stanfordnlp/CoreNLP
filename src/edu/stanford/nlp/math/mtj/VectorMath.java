package edu.stanford.nlp.math.mtj;

import no.uib.cipr.matrix.AbstractVector;
import no.uib.cipr.matrix.Vector;
import no.uib.cipr.matrix.VectorEntry;
import edu.stanford.cs.ra.RA;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Index;

/**
 * Extra math functions for MTJ Vectors.
 * 
 * @author dramage
 */
public class VectorMath {

//  /**
//   * An iterator over a pair of entries in two vectors.
//   */
//  public interface VectorPairEntry {
//    /** Returns the current index (in both vectors) */
//    public int index();
//    
//    /** Returns the value in the first vector */
//    public double getA();
//    
//    /** Returns the value in the second vector */
//    public double getB();
//    
//    /** Sets the value in the first vector */
//    public void setA(double value);
//    
//    /** Sets the value in the second vector */
//    public void setB(double value);
//  }
//  
//  /**
//   * Zips together the entries of the two vectors that are both non-zero.
//   */
//  public Iterable<VectorPairEntry> zipInner(Vector a, Vector b) {
//    return new Iterable<VectorPairEntry>() {
//      public Iterator<VectorPairEntry> iterator() {
//        return new Iterator<VectorPairEntry>() {
//          VectorPairEntry entry = null;
//
//          @Override
//          public boolean hasNext() {
//            
//          }
//
//          @Override
//          public VectorPairEntry next() {
//            // TODO Auto-generated method stub
//            return null;
//          }
//
//          @Override
//          public void remove() {
//            // TODO Auto-generated method stub
//            
//          }
//          
//        };
//      }
//    };
//  }
  
  /**
   * Sets all entries of x to RA.random(); returns x. 
   */
  public static <V extends Vector> V random(V x) {
    for (VectorEntry entry : x) {
      entry.set(RA.random());
    }
    return x;
  }

  /**
   * Sets x_i = exp(x_i); returns x.
   */
  public static Vector elementExp(Vector x) {
    for (VectorEntry entry : x) {
      entry.set(Math.exp(entry.get()));
    }
    return x;
  }

  /**
   * Sets x_i = pow(x_i, b); returns x.
   */
  public static Vector elementPow(Vector x, double b) {
    for (VectorEntry entry : x) {
      entry.set(Math.pow(entry.get(), b));
    }
    return x;
  }

  /**
   * Sets x_i = log(x_i); returns x.
   */
  public static Vector elementLog(Vector x) {
    for (VectorEntry entry : x) {
      entry.set(Math.log(entry.get()));
    }
    return x;
  }

  /**
   * Normalized the matrix.
   */
  public static void normalize(Vector v) {
    v.scale(1.0/v.norm(Vector.Norm.One));
  }

  /** Returns a read-only vector of all ones of the given size */
  public static Vector ones(int size) {
    return new AbstractVector(size) {

      @Override
      public double get(int index) {
        return 1;
      }

      private static final long serialVersionUID = 1L;

    };
  }

  /** Returns equals(a,b,1e-8) */
  public static boolean equals(Vector a, Vector b) {
    return equals(a,b,1e-8);
  }
  
  /**
   * Returns true if the vectors are the same length and have the
   * same elements.
   */
  public static boolean equals(Vector a, Vector b, double tolerance) {
    if (a.size() != b.size()) {
      return false;
    }
    
    final int n = a.size();
    for (int i = 0; i < n; i++) {
      if (Math.abs(a.get(i) - b.get(i)) > tolerance) {
        return false;
      }
    }
    
    return true;
  }

  /** Converts to a counter */
  public static <T> Counter<T> toCounter(Vector vector, Index<T> index) {
    Counter<T> counter = new ClassicCounter<T>();
    for (VectorEntry entry : vector) {
      counter.setCount(index.get(entry.index()), entry.get());
    }
    return counter;
  }
}
