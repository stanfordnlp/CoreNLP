package edu.stanford.nlp.math.mtj;

import no.uib.cipr.matrix.AbstractMatrix;
import no.uib.cipr.matrix.Matrix;

/**
 * A Matrix that is a re-mapped view of the given matrix according to the
 * given mapping.
 * 
 * @author dramage
 */
public class MappedMatrix extends AbstractMatrix {

  /** Wrapped inner matrix */
  public final Matrix inner;
  
  /** Map for mapping rows to new rows, columns to new columns */
  public final MappedMatrixMap rowMap;
  public final MappedMatrixMap colMap;
  
  public MappedMatrix(Matrix inner, MappedMatrixMap rowMap, MappedMatrixMap colMap) {
    super(rowMap.outerSize(), colMap.outerSize());
    this.inner = inner;
    this.rowMap = rowMap;
    this.colMap = colMap;
  }
  
  @Override
  public void set(int row, int col, double value) {
    final int innerRow = rowMap.toInner(row), innerCol = colMap.toInner(col);
    if (innerRow < 0 || innerCol < 0) {
      throw new IllegalArgumentException("Mapped matrix does not have "+row+","+col);
    }
    
    inner.set(innerRow, innerCol, value);
  }

  @Override
  public void add(int row, int col, double value) {
    final int innerRow = rowMap.toInner(row), innerCol = colMap.toInner(col);
    if (innerRow < 0 || innerCol < 0) {
      throw new IllegalArgumentException("Mapped matrix does not have "+row+","+col);
    }
    
    inner.add(innerRow, innerCol, value);
  }

  @Override
  public double get(int row, int col) {
    final int innerRow = rowMap.toInner(row), innerCol = colMap.toInner(col);
    if (innerRow < 0 || innerCol < 0) {
      throw new IllegalArgumentException("Mapped matrix does not have "+row+","+col);
    }
    
    return inner.get(innerRow, innerCol);
  }
  
  /** A map for converting coordinates from an outer matrix to inner matrix */
  public interface MappedMatrixMap {
    public int toInner(int outer);
    public int outerSize();
  }
  
}
