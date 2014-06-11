package edu.stanford.nlp.rnn;

import java.util.Iterator;
import java.util.Map;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.Generics;

public class RNNUtils {
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

  public static Map<String, SimpleMatrix> readRawWordVectors(String filename, int expectedSize) {
    Map<String, SimpleMatrix> wordVectors = Generics.newHashMap();

    System.err.println("Reading in the word vector file: " + filename);
    int dimOfWords = 0;
    boolean warned = false;
    for (String line : IOUtils.readLines(filename, "utf-8")) {
      String[]  lineSplit = line.split("\\s+");
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
}
