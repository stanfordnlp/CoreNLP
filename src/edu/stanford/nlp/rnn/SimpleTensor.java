package edu.stanford.nlp.rnn;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.ejml.simple.SimpleMatrix;

public class SimpleTensor implements Serializable {
  private SimpleMatrix[] slices;

  final int numRows;
  final int numCols;
  final int numSlices;

  public SimpleTensor(int numRows, int numCols, int numSlices) {
    slices = new SimpleMatrix[numSlices];
    for (int i = 0; i < numSlices; ++i) {
      slices[i] = new SimpleMatrix(numRows, numCols);
    }

    this.numRows = numRows;
    this.numCols = numCols;
    this.numSlices = numSlices;
  }

  public static SimpleTensor random(int numRows, int numCols, int numSlices, double minValue, double maxValue, java.util.Random rand) {
    SimpleTensor tensor = new SimpleTensor(numRows, numCols, numSlices);
    for (int i = 0; i < numSlices; ++i) {
      tensor.slices[i] = SimpleMatrix.random(numRows, numCols, minValue, maxValue, rand);
    }
    return tensor;
  }

  public int numRows() {
    return numRows;
  }

  public int numCols() {
    return numCols;
  }

  public int numSlices() {
    return numSlices;
  }

  public int getNumElements() {
    return numRows * numCols * numSlices;
  }

  public SimpleTensor scale(double scaling) {
    SimpleTensor result = new SimpleTensor(numRows, numCols, numSlices);
    for (int slice = 0; slice < numSlices; ++slice) {
      result.slices[slice] = slices[slice].scale(scaling);
    }
    return result;
  }

  public SimpleTensor plus(SimpleTensor other) {
    if (other.numRows != numRows || other.numCols != numCols || other.numSlices != numSlices) {
      throw new IllegalArgumentException("Sizes of tensors do not match.  Our size: " + numRows + "," + numCols + "," + numSlices + "; other size " + other.numRows + "," + other.numCols + "," + other.numSlices);
    }
    SimpleTensor result = new SimpleTensor(numRows, numCols, numSlices);
    for (int i = 0; i < numSlices; ++i) {
      result.slices[i] = slices[i].plus(other.slices[i]);
    }
    return result;
  }

  public void setSlice(int slice, SimpleMatrix matrix) {
    if (slice < 0 || slice >= numSlices) {
      throw new IllegalArgumentException("Unexpected slice number " + slice + " for tensor with " + numSlices + " slices");
    }
    if (matrix.numCols() != numCols) {
      throw new IllegalArgumentException("Incompatible matrix size.  Has " + matrix.numCols() + " columns, tensor has " + numCols);
    }
    if (matrix.numRows() != numRows) {
      throw new IllegalArgumentException("Incompatible matrix size.  Has " + matrix.numRows() + " columns, tensor has " + numRows);
    }
    slices[slice] = matrix;
  }

  public SimpleMatrix getSlice(int slice) {
    if (slice < 0 || slice >= numSlices) {
      throw new IllegalArgumentException("Unexpected slice number " + slice + " for tensor with " + numSlices + " slices");
    }
    return slices[slice];
  }

  public SimpleMatrix bilinearProducts(SimpleMatrix in) {
    if (in.numCols() != 1) {
      throw new AssertionError("Expected a column vector");
    }
    if (in.numRows() != numCols) {
      throw new AssertionError("Number of rows in the input does not match number of columns in tensor");
    }
    if (numRows != numCols) {
      throw new AssertionError("Can only perform this operation on a SimpleTensor with square slices");
    }
    SimpleMatrix inT = in.transpose();
    SimpleMatrix out = new SimpleMatrix(numSlices, 1);
    for (int slice = 0; slice < numSlices; ++slice) {
      double result = inT.mult(slices[slice]).mult(in).get(0);
      out.set(slice, result);
    }
    return out;
  }

  public Iterator<SimpleMatrix> iteratorSimpleMatrix() {
    return Arrays.asList(slices).iterator();
  }

  /**
   * Returns an Iterator which returns the SimpleMatrices represented
   * by an Iterator over tensors.  This is useful for if you want to
   * perform some operation on each of the SimpleMatrix slices, such
   * as turning them into a parameter stack.
   */
  public static Iterator<SimpleMatrix> iteratorSimpleMatrix(Iterator<SimpleTensor> tensors) {
    return new SimpleMatrixIteratorWrapper(tensors);
  }

  private static class SimpleMatrixIteratorWrapper implements Iterator<SimpleMatrix> {
    Iterator<SimpleTensor> tensors;
    Iterator<SimpleMatrix> currentIterator;

    public SimpleMatrixIteratorWrapper(Iterator<SimpleTensor> tensors) {
      this.tensors = tensors;
      advanceIterator();
    }

    public boolean hasNext() {
      if (currentIterator == null) {
        return false;
      }
      if (currentIterator.hasNext()) {
        return true;
      }
      advanceIterator();
      return (currentIterator != null);
    }

    public SimpleMatrix next() {
      if (currentIterator != null && currentIterator.hasNext()) {
        return currentIterator.next();
      }
      advanceIterator();
      if (currentIterator != null) {
        return currentIterator.next();
      }
      throw new NoSuchElementException();
    }

    private void advanceIterator() {
      if (currentIterator != null && currentIterator.hasNext()) {
        return;
      }
      while (tensors.hasNext()) {
        currentIterator = tensors.next().iteratorSimpleMatrix();
        if (currentIterator.hasNext()) {
          return;
        }
      }
      currentIterator = null;
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  private static final long serialVersionUID = 1;
}
