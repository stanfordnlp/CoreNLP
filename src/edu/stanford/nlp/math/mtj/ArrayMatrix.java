package edu.stanford.nlp.math.mtj;

import no.uib.cipr.matrix.AbstractMatrix;
import no.uib.cipr.matrix.Matrix;

/**
 * Matrix backed directly by an array of double[][]
 *
 * @author dramage
 */
public class ArrayMatrix extends AbstractMatrix {

  /** Wrapped data structure */
  public final double[][] data;

  /** Constructs this ArrayMatrix to have a view of the given data */
  public ArrayMatrix(double[][] data) {
    super(data.length, data[0].length);
    this.data = data;
  }

  @Override
  public void set(int row, int column, double value) {
      data[row][column] = value;
  }

  @Override
  public double get(int row, int column) {
      return data[row][column];
  }

  @Override
  public Matrix copy() {
    double[][] dataCopy = new double[numRows][numColumns];
    for (int i = 0; i < numRows; i++) {
      System.arraycopy(this.data[i], 0, dataCopy[i], 0, numColumns);
    }
    return new ArrayMatrix(dataCopy);
  }

}
