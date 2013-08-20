package edu.stanford.nlp.math.mtj;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.util.Iterator;

import no.uib.cipr.matrix.AbstractMatrix;
import no.uib.cipr.matrix.Matrix;
import no.uib.cipr.matrix.MatrixEntry;

/**
 * A MergedMatrix merges k- input matrices (with different sparsity patterns),
 * accessing/updating whichever is the first matrix to have a non-zero element
 * in any given requested position.
 * 
 * @author dramage
 */
public class MergedMatrix extends AbstractMatrix {
  
  /** Underlying mixture matrices */
  final Matrix[] underlying;
  
  final Int2ObjectOpenHashMap<Int2IntOpenHashMap> which;
  
  /**
   * Constructs a merged view of the given underlying matrices.  The
   * dimensionality of this matrix will be the max rows by the max columns
   * (where max is taken over all underlying matrices).  Asserts that the
   * matrices have non-overlapping sparsity.
   */
  public MergedMatrix(Matrix ... underlying) {
    super(maxRows(underlying), maxColumns(underlying));
    
    this.underlying = underlying;
    
    this.which = new Int2ObjectOpenHashMap<Int2IntOpenHashMap>();
    
    for (int i = 0; i < underlying.length; i++) {
      for (MatrixEntry entry : underlying[i]) {
        if (entry.get() == 0.0) {
          continue;
        }
        
        final int row = entry.row();
        final int col = entry.column();
        
        Int2IntOpenHashMap colpointers = which.get(row);
        if (colpointers == null) {
          colpointers = new Int2IntOpenHashMap();
          colpointers.defaultReturnValue(-1);
          which.put(row, colpointers);
        }
        
        // assert that the sparsity pattern is non-overlapping
        if (colpointers.containsKey(col)) {
          throw new IllegalArgumentException("Entry "+row+","+col+" seen in " +
             "more than one matrix: in "+i+" and "+colpointers.get(col));
        }
        colpointers.put(col, i);
      }
    }
  }
  
  @Override
  public void set(int row, int col, double value) {
    final Matrix m = whichMatrix(row,col);
    if (m == null) {
      throw new IllegalArgumentException("No matrix provides ("+row+","+col+")");
    }
    m.set(row,col,value);
  }

  @Override
  public void add(int row, int col, double value) {
    final Matrix m = whichMatrix(row,col);
    if (m == null) {
      throw new IllegalArgumentException("No matrix provides ("+row+","+col+")");
    }
    m.add(row,col,value);
  }
  
  @Override
  public double get(int row, int col) {
    final Matrix m = whichMatrix(row,col);
    return m == null ? 0.0 : m.get(row,col);
  }
  
  @Override
  public Iterator<MatrixEntry> iterator() {
    return new Iterator<MatrixEntry>() {
      final MergedMatrixEntry entry = new MergedMatrixEntry();
      
      final ObjectIterator<Int2ObjectMap.Entry<Int2IntOpenHashMap>> rows =
        which.int2ObjectEntrySet().fastIterator();
      
      Int2ObjectMap.Entry<Int2IntOpenHashMap> row = null;
      ObjectIterator<Int2IntMap.Entry> col = null;
      
      public boolean hasNext() {
        if (col != null && col.hasNext()) {
          return true;
        } else if (rows.hasNext()) {
          row = rows.next();
          col = row.getValue().int2IntEntrySet().fastIterator();
          return hasNext();
        } else {
          col = null;
          return false;
        }
      }

      public MatrixEntry next() {
        Int2IntMap.Entry e = col.next();
        
        entry.row = row.getIntKey();
        entry.col = e.getIntKey();
        entry.matrix = underlying[e.getIntValue()];
        
        return entry;
      }

      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
    
//    return Iterables.chain(Iterables.transform(Arrays.asList(underlying),
//        new Function<Iterable<MatrixEntry>,Iterable<MatrixEntry>>() {
//          public Iterable<MatrixEntry> apply(Iterable<MatrixEntry> in) {
//            return Iterables.filter(in, new Function<MatrixEntry,Boolean>() {
//              public Boolean apply(MatrixEntry entry) {
//                return entry.get() != 0.0;
//              }
//            });
//          }
//        }
//    )).iterator();
  }
  
  /**
   * Returns the first matrix for which row < numRows and col < numColumns
   * and the current value is non-zero; or null if no matrix
   * satisfies those conditions.
   */
  private final Matrix whichMatrix(int row, int col) {
    final Int2IntMap colpointers = which.get(row);
    if (colpointers == null) {
      return null;
    }
    final int matrix = colpointers.get(col);
    if (matrix < 0) {
      return null;
    }
    return underlying[matrix];
  }
  
  /**
   * Entry into the MergedMatrix.
   */
  private static class MergedMatrixEntry implements MatrixEntry {
    private Matrix matrix;
    private int row;
    private int col;
    
    public int row() {
      return row;
    }

    public int column() {
      return col;
    }

    public double get() {
      return matrix.get(row,col);
    }

    public void set(double value) {
      matrix.set(row, col, value);
    }
  }
  
//  private void checkrep() {
//    Set<Pair<Integer,Integer>> seen = new HashSet<Pair<Integer,Integer>>();
//    for (Matrix matrix : underlying) {
//      for (MatrixEntry entry : matrix) {
//        if (entry.get() == 0.0) {
//          continue;
//        }
//        final int row = entry.row(), col = entry.column();
//        Pair<Integer,Integer> pair = new Pair<Integer,Integer>(row, col);
//        if (seen.contains(pair)) {
//          throw new IllegalArgumentException("Entry "+row+","+col+" seen in " +
//              "more than one matrix");
//        }
//        seen.add(pair);
//      }
//    }
//  }
  
  //
  // utility methods
  //
  
  /** Returns the max number of rows in the given array of matrices */
  private static int maxRows(Matrix ... underlying) {
    int max = 0;
    for (Matrix m : underlying) {
      if (m.numRows() > max) {
        max = m.numRows();
      }
    }
    return max;
  }

  /** Returns the max number of columns in the given array of matrices */
  private static int maxColumns(Matrix ... underlying) {
    int max = 0;
    for (Matrix m : underlying) {
      if (m.numColumns() > max) {
        max = m.numColumns();
      }
    }
    return max;
  }
}
