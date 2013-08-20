package edu.stanford.nlp.math.mtj;

import junit.framework.TestCase;
import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.Matrix;
import no.uib.cipr.matrix.MatrixEntry;

/**
 * Tests methods in MergedMatrix.
 * 
 * @author dramage
 */
public class MergedMatrixTest extends TestCase {

  public void testCheckInvalidSparsity() {
    boolean caught = false;
    try {
      new MergedMatrix(
          new DenseMatrix(new double[][]{{1,2},{0,1}}),
          new DenseMatrix(new double[][]{{0,0},{1,1}})
      );
    } catch (Exception e) {
      caught = true;
    }
    
    assertTrue("Didn't throw exception on invalid sparsity", caught);
  }
  
  public void testMergedView() {
    MergedMatrix merged = new MergedMatrix(
        new DenseMatrix(new double[][]{{1,0,3},{0,5,0}}),
        new DenseMatrix(new double[][]{{0,-2},{-3,0}}));
    
    assertTrue("Matrix not equal", equals(merged,
        new DenseMatrix(new double[][]{{1,-2,3},{-3,5,0}})));
    
    double[][] mergedArray = new double[2][3];
    for (int row = 0; row < mergedArray.length; row++) {
      for (int col = 0; col < mergedArray[row].length; col++) {
        mergedArray[row][col] = merged.get(row, col);
      }
    }
    
    assertTrue("Matrix not equal", equals(
        new DenseMatrix(mergedArray),
        new DenseMatrix(new double[][]{{1,-2,3},{-3,5,0}})));
  }
  
  /**
   * Returns true if the two matrices are component-wise equal.  This
   * algorithm completely ignores structure.  It uses a's matrix iterator,
   * but just calls get() for each corresponding entry on b.  Hence,
   * if only one matrix is dense or hashable, that should be the second
   * argument.
   */
  public static boolean equals(Matrix a, Matrix b) {
    if (a == null && b == null) {
      return true;
    } else if (a == null || b == null) {
      return false;
    } else if (a.numRows() != b.numRows() || a.numColumns() != b.numColumns()) {
      return false;
    }

    for (MatrixEntry entry : a) {
      if (entry.get() != b.get(entry.row(), entry.column())) {
        return false;
      }
    }
    
    for (MatrixEntry entry : b) {
      if (entry.get() != a.get(entry.row(), entry.column())) {
        return false;
      }
    }

    return true;
  }
  
}
