package edu.stanford.nlp.rnn;

import java.util.Iterator;
import java.util.Map;
import java.util.Random;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.Generics;

/**
 * Includes a bunch of utility methods usable by projects which use
 * RNN, such as the parser and sentiment models.  Some methods convert
 * iterators of SimpleMatrix objects to and from a vector.  Others are
 * general utility methods on SimpleMatrix objects.
 *
 * @author John Bauer
 * @author Richard Socher
 */
public class RNNUtils {
  private RNNUtils() {} // static methods only

  /**
   * Given a sequence of Iterators over SimpleMatrix, fill in all of
   * the matrices with the entries in the theta vector.  Errors are
   * thrown if the theta vector does not exactly fill the matrices.
   */
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
   * according to the <code>scale</code> parameter.  Asks for an
   * expected total size as a time savings.  AssertionError thrown if
   * the vector sizes do not exactly match.
   */
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
   * This method reads a file of raw word vectors, with a given expected size, and returns a map of word to vector.
   * <br>
   * The file should be in the format <br>
   * <code>WORD X1 X2 X3 ...</code> <br>
   * If vectors in the file are smaller than expectedSize, an
   * exception is thrown.  If vectors are larger, the vectors are
   * trunccated and a warning is printed.
   */
  public static Map<String, SimpleMatrix> readRawWordVectors(String filename, int expectedSize) {
    Map<String, SimpleMatrix> wordVectors = Generics.newHashMap();

    System.err.println("Reading in the word vector file: " + filename);
    int dimOfWords = 0;
    boolean warned = false;
    for (String line : IOUtils.readLines(filename, "utf-8")) {
      String[] lineSplit = line.split("\\s+");
      String word = lineSplit[0];
      dimOfWords = lineSplit.length - 1;
      if (expectedSize <= 0) {
        expectedSize = dimOfWords;
        System.err.println("Dimensionality of numHid not set.  The length of the word vectors in the given file appears to be " + dimOfWords);
      }
      // the first entry is the word itself
      // the other entries will all be entries in the word vector
      if (dimOfWords > expectedSize) {
        if (!warned) {
          warned = true;
          System.err.println("WARNING: Dimensionality of numHid parameter and word vectors do not match, deleting word vector dimensions to fit!");
        }
        dimOfWords = expectedSize;
      } else if (dimOfWords < expectedSize) {
        throw new RuntimeException("Word vectors file has dimension too small for requested numHid of " + expectedSize);
      }
      double vec[][] = new double[dimOfWords][1];
      for (int i = 1; i <= dimOfWords; i++) {
        vec[i-1][0] = Double.parseDouble(lineSplit[i]);
      }
      SimpleMatrix vector = new SimpleMatrix(vec);
      wordVectors.put(word, vector);
    }
 
    return wordVectors;
  }



  /**
   * Returns a sigmoid applied to the input <code>x</code>.
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
    output.set(1.0);
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

}
