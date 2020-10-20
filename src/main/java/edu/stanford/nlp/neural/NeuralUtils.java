package edu.stanford.nlp.neural;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.ejml.data.DMatrix;
import org.ejml.ops.MatrixIO;
import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.CollectionUtils;

/**
 * Includes a bunch of utility methods usable by projects which use
 * RNN, such as the parser and sentiment models.  Some methods convert
 * iterators of SimpleMatrix objects to and from a vector.  Others are
 * general utility methods on SimpleMatrix objects.
 *
 * @author John Bauer
 * @author Richard Socher
 * @author Thang Luong
 * @author Kevin Clark
 */
public class NeuralUtils {
  private NeuralUtils() {} // static methods only

  /**
   * Convert a file into a text matrix.  The expected format one row
   * per line, one entry per column.  Not too efficient for large
   * matrices, but you shouldn't store large matrices in text files
   * anyway.  This specific format is not supported by ejml, which
   * expects the number of rows and columns in its text matrices.
   */
  public static SimpleMatrix loadTextMatrix(String path) {
    return convertTextMatrix(IOUtils.slurpFileNoExceptions(path));
  }

  /**
   * Convert a file into a text matrix.  The expected format one row
   * per line, one entry per column.  Not too efficient for large
   * matrices, but you shouldn't store large matrices in text files
   * anyway.  This specific format is not supported by ejml, which
   * expects the number of rows and columns in its text matrices.
   */
  public static SimpleMatrix loadTextMatrix(File file) {
    return convertTextMatrix(IOUtils.slurpFileNoExceptions(file));
  }

  /**
   * Convert a file into a list of matrices. The expected format is one row
   * per line, one entry per column for each matrix, with each matrix separated
   * by an empty line.
   */
  public static List<SimpleMatrix> loadTextMatrices(String path) {
    List<SimpleMatrix> matrices = new ArrayList<>();
    for (String mString : IOUtils.stringFromFile(path).trim().split("\n\n")) {
      matrices.add(NeuralUtils.convertTextMatrix(mString).transpose());
    }
    return matrices;
  }

  public static SimpleMatrix convertTextMatrix(String text) {
    List<String> lines = CollectionUtils.filterAsList(Arrays.asList(text.split("\n")),
            s -> ! s.trim().isEmpty());
    int numRows = lines.size();
    int numCols = lines.get(0).trim().split("\\s+").length;
    double[][] data = new double[numRows][numCols];
    for (int row = 0; row < numRows; ++row) {
      String line = lines.get(row);
      String[] pieces = line.trim().split("\\s+");
      if (pieces.length != numCols) {
        throw new RuntimeException("Unexpected row length in line " + row);
      }
      for (int col = 0; col < numCols; ++col) {
        data[row][col] = Double.valueOf(pieces[col]);
      }
    }
    return new SimpleMatrix(data);
  }

  /**
   * @param matrix The matrix to return as a String
   * @param format The format to use for each value in the matrix, eg "%f"
   */
  public static String toString(SimpleMatrix matrix, String format) {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    MatrixIO.print(new PrintStream(stream), (DMatrix) matrix.getMatrix(), format);
    return stream.toString();
  }

  /**
   * Compute cosine distance between two column vectors.
   */
  public static double cosine(SimpleMatrix vector1, SimpleMatrix vector2){
    return dot(vector1, vector2)/(vector1.normF()*vector2.normF());
  }

  /**
   * Compute dot product between two vectors.
   */
  public static double dot(SimpleMatrix vector1, SimpleMatrix vector2){
    if(vector1.numRows()==1){ // vector1: row vector, assume that vector2 is a row vector too
      return vector1.mult(vector2.transpose()).get(0);
    } else if (vector1.numCols()==1){ // vector1: col vector, assume that vector2 is also a column vector.
      return vector1.transpose().mult(vector2).get(0);
    } else {
      throw new AssertionError("Error in neural.Utils.dot: vector1 is a matrix " + vector1.numRows() + " x " + vector1.numCols());
    }
  }

  /**
   * Given a sequence of Iterators over SimpleMatrix, fill in all of
   * the matrices with the entries in the theta vector.  Errors are
   * thrown if the theta vector does not exactly fill the matrices.
   */
  @SafeVarargs
  public static void vectorToParams(double[] theta, Iterator<SimpleMatrix> ... matrices) {
    int index = 0;
    for (Iterator<SimpleMatrix> matrixIterator : matrices) {
      while (matrixIterator.hasNext()) {
        SimpleMatrix matrix = matrixIterator.next();
        int numElements = matrix.getNumElements();
        for (int i = 0; i < numElements; ++i) {
          matrix.set(i, theta[index]);
          ++index;
        }
      }
    }
    if (index != theta.length) {
      throw new AssertionError("Did not entirely use the theta vector");
    }
  }

  /**
   * Given a sequence of iterators over the matrices, builds a vector
   * out of those matrices in the order given.  Asks for an expected
   * total size as a time savings.  AssertionError thrown if the
   * vector sizes do not exactly match.
   */
  @SafeVarargs
  public static double[] paramsToVector(int totalSize, Iterator<SimpleMatrix> ... matrices) {
    double[] theta = new double[totalSize];
    int index = 0;
    for (Iterator<SimpleMatrix> matrixIterator : matrices) {
      while (matrixIterator.hasNext()) {
        SimpleMatrix matrix = matrixIterator.next();
        int numElements = matrix.getNumElements();
        //System.out.println(Integer.toString(numElements)); // to know what matrices are
        for (int i = 0; i < numElements; ++i) {
          theta[index] = matrix.get(i);
          ++index;
        }
      }
    }
    if (index != totalSize) {
      throw new AssertionError("Did not entirely fill the theta vector: expected " + totalSize + " used " + index);
    }
    return theta;
  }

  /**
   * Given a sequence of iterators over the matrices, builds a vector
   * out of those matrices in the order given.  The vector is scaled
   * according to the {@code scale} parameter.  Asks for an
   * expected total size as a time savings.  AssertionError thrown if
   * the vector sizes do not exactly match.
   */
  @SafeVarargs
  public static double[] paramsToVector(double scale, int totalSize, Iterator<SimpleMatrix> ... matrices) {
    double[] theta = new double[totalSize];
    int index = 0;
    for (Iterator<SimpleMatrix> matrixIterator : matrices) {
      while (matrixIterator.hasNext()) {
        SimpleMatrix matrix = matrixIterator.next();
        int numElements = matrix.getNumElements();
        for (int i = 0; i < numElements; ++i) {
          theta[index] = matrix.get(i) * scale;
          ++index;
        }
      }
    }
    if (index != totalSize) {
      throw new AssertionError("Did not entirely fill the theta vector: expected " + totalSize + " used " + index);
    }
    return theta;
  }

  /**
   * Returns a sigmoid applied to the input {@code x}.
   */
  public static double sigmoid(double x) {
    return 1.0 / (1.0 + Math.exp(-x));
  }

  /**
   * Applies softmax to all of the elements of the matrix.  The return
   * matrix will have all of its elements sum to 1.  If your matrix is
   * not already a vector, be sure this is what you actually want.
   */
  public static SimpleMatrix softmax(SimpleMatrix input) {
    SimpleMatrix output = new SimpleMatrix(input);
    for (int i = 0; i < output.numRows(); ++i) {
      for (int j = 0; j < output.numCols(); ++j) {
        output.set(i, j, Math.exp(output.get(i, j)));
      }
    }
    double sum = output.elementSum();
    // will be safe, since exp should never return 0
    return output.scale(1.0 / sum);
  }

  /**
   * Applies ReLU to each of the entries in the matrix.  Returns a new matrix.
   */
  public static SimpleMatrix elementwiseApplyReLU(SimpleMatrix input) {
    SimpleMatrix output = new SimpleMatrix(input);
    for (int i = 0; i < output.numRows(); ++i) {
      for (int j = 0; j < output.numCols(); ++j) {
        output.set(i, j, Math.max(0, output.get(i, j)));
      }
    }
    return output;
  }

  /**
   * Applies log to each of the entries in the matrix.  Returns a new matrix.
   */
  public static SimpleMatrix elementwiseApplyLog(SimpleMatrix input) {
    SimpleMatrix output = new SimpleMatrix(input);
    for (int i = 0; i < output.numRows(); ++i) {
      for (int j = 0; j < output.numCols(); ++j) {
        output.set(i, j, Math.log(output.get(i, j)));
      }
    }
    return output;
  }

  /**
   * Applies tanh to each of the entries in the matrix.  Returns a new matrix.
   */
  public static SimpleMatrix elementwiseApplyTanh(SimpleMatrix input) {
    SimpleMatrix output = new SimpleMatrix(input);
    for (int i = 0; i < output.numRows(); ++i) {
      for (int j = 0; j < output.numCols(); ++j) {
        output.set(i, j, Math.tanh(output.get(i, j)));
      }
    }
    return output;
  }

  /**
   * Applies the derivative of tanh to each of the elements in the vector.  Returns a new matrix.
   */
  public static SimpleMatrix elementwiseApplyTanhDerivative(SimpleMatrix input) {
    SimpleMatrix output = new SimpleMatrix(input.numRows(), input.numCols());
    output.fill(1.0);
    output = output.minus(input.elementMult(input));
    return output;
  }

  /**
   * Concatenates several column vectors into one large column
   * vector, adds a 1.0 at the end as a bias term
   */
  public static SimpleMatrix concatenateWithBias(SimpleMatrix ... vectors) {
    int size = 0;
    for (SimpleMatrix vector : vectors) {
      size += vector.numRows();
    }
    // one extra for the bias
    size++;

    SimpleMatrix result = new SimpleMatrix(size, 1);
    int index = 0;
    for (SimpleMatrix vector : vectors) {
      result.insertIntoThis(index, 0, vector);
      index += vector.numRows();
    }
    result.set(index, 0, 1.0);
    return result;
  }

  /**
   * Concatenates several column vectors into one large column vector
   */
  public static SimpleMatrix concatenate(SimpleMatrix ... vectors) {
    int size = 0;
    for (SimpleMatrix vector : vectors) {
      size += vector.numRows();
    }

    SimpleMatrix result = new SimpleMatrix(size, 1);
    int index = 0;
    for (SimpleMatrix vector : vectors) {
      result.insertIntoThis(index, 0, vector);
      index += vector.numRows();
    }
    return result;
  }

  /**
   * Returns a vector with random Gaussian values, mean 0, std 1
   */
  public static SimpleMatrix randomGaussian(int numRows, int numCols, Random rand) {
    SimpleMatrix result = new SimpleMatrix(numRows, numCols);
    for (int i = 0; i < numRows; ++i) {
      for (int j = 0; j < numCols; ++j) {
        result.set(i, j, rand.nextGaussian());
      }
    }
    return result;
  }

  public static SimpleMatrix oneHot(int index, int size) {
    SimpleMatrix m = new SimpleMatrix(size, 1);
    m.set(index, 1);
    return m;
  }

  /**
   * Returns true iff every element of matrix is 0
   */
  public static boolean isZero(SimpleMatrix matrix) {
    int size = matrix.getNumElements();
    for (int i = 0; i < size; ++i) {
      if (matrix.get(i) != 0.0) {
        return false;
      }
    }
    return true;
  }

}

