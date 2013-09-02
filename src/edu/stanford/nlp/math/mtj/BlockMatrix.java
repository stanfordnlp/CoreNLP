package edu.stanford.nlp.math.mtj;

import no.uib.cipr.matrix.AbstractMatrix;
import no.uib.cipr.matrix.Matrix;

/**
 * A block matrix represented as the concatenation of several underlying
 * matrices.
 * 
 * @author dramage
 */
public class BlockMatrix extends AbstractMatrix {
  
  /** Number of rows and columsn of A */
  private final int topRows, leftCols;
  
  /** Matrices ordered as {{a,b},{c,d}} */
  private final Matrix m[][];
  
  /**
   * Makes one matrix by composing into one:
   * 
   * <pre>
   *   [ a b ]
   *   [ c d ]
   *  </pre>
   */
  public BlockMatrix(Matrix a, Matrix b, Matrix c, Matrix d) {
    super(a.numRows() + d.numRows(), a.numColumns() + b.numColumns());
    
    if (a.numRows() != b.numRows()) {
      throw new IllegalArgumentException("A and B must have same # rows");
    }
    if (a.numColumns() != c.numColumns()) {
      throw new IllegalArgumentException("A and C must have same # cols");
    }
    if (c.numRows() != d.numRows()) {
      throw new IllegalArgumentException("C and D must have same # rows");
    }
    if (b.numColumns() != d.numColumns()) {
      throw new IllegalArgumentException("B and D must have same # cols");
    }
    
    this.topRows  = a.numRows();
    this.leftCols = a.numColumns();
    this.m = new Matrix[][]{{a,b},{c,d}};
  }
  
  @Override
  public void set(int row, int col, double value) {
    int blockRow = 0, blockCol = 0;
    if (row > topRows) {
      blockRow = 1;
      row -= topRows;
    }
    if (col > leftCols) {
      blockCol = 1;
      col -= leftCols;
    }
    
    m[blockRow][blockCol].set(row,col,value);
  }

  @Override
  public double get(int row, int col) {
    int blockRow = 0, blockCol = 0;
    if (row > topRows) {
      blockRow = 1;
      row -= topRows;
    }
    if (col > leftCols) {
      blockCol = 1;
      col -= leftCols;
    }
    
    return m[blockRow][blockCol].get(row,col);
  }
}
