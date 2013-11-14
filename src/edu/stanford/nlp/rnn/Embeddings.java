/**
 * 
 */
package edu.stanford.nlp.rnn;

import java.util.Iterator;
import java.util.Map;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.Generics;

/**
 * @author John Bauer
 * @author Richard Socher
 * @author Minh-Thang Luong <lmthang@stanford.edu>
 *
 */
public class Embeddings {  
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
   * This method takes as input two files: wordFile (one word per line) and a raw word vector file
   * with a given expected size, and returns a map of word to vector.
   * <br>
   * The word vector file should be in the format <br>
   * <code>X1 X2 X3 ...</code> <br>
   * If vectors in the file are smaller than expectedSize, an
   * exception is thrown.  If vectors are larger, the vectors are
   * trunccated and a warning is printed.
   */
  public static Map<String, SimpleMatrix> readRawWordVectors(String wordFile, String vectorFile, int expectedSize) {
    Map<String, SimpleMatrix> wordVectors = Generics.newHashMap();

    System.err.println("Reading in the word file " + wordFile + " and vector file " + vectorFile);
    int dimOfWords = 0;
    boolean warned = false;
    
    Iterator<String> wordIterator = IOUtils.readLines(vectorFile, "utf-8").iterator();
    for (String line : IOUtils.readLines(vectorFile, "utf-8")) {
      String[] lineSplit = line.split("\\s+");
      String word = wordIterator.next();
      dimOfWords = lineSplit.length;
      
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
      for (int i = 0; i < dimOfWords; i++) {
        vec[i][0] = Double.parseDouble(lineSplit[i]);
      }
      SimpleMatrix vector = new SimpleMatrix(vec);
      wordVectors.put(word, vector);
    }
 
    return wordVectors;
  }
}
