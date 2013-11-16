/**
 * 
 */
package edu.stanford.nlp.neural;

import static org.junit.Assert.*;

import org.ejml.simple.SimpleMatrix;
import org.junit.Test;

/**
 * @author Minh-Thang Luong <lmthang@stanford.edu>, created on Nov 14, 2013
 *
 */

public class EmbeddingTest {
  public static final String PREFIX = "projects/core/";
  public static final String wordVectorFile = PREFIX + "data/edu/stanford/nlp/neural/wordVector.txt";
  public static final String wordFile = PREFIX + "data/edu/stanford/nlp/neural/word.txt";
  public static final String vectorFile = PREFIX + "data/edu/stanford/nlp/neural/vector.txt";
  
  @Test
  public void testLoadFromOneFile() {
    Embedding embedding = new Embedding(wordVectorFile);
    
    SimpleMatrix vector;
    double[][] values = new double[1][5];
    
    // unknown
    values[0] = new double[]{0.1, 0.2, 0.3, 0.4, 0.5};
    vector = embedding.getUnknownWordVector();
    vector.reshape(1, 5);
    assertTrue(vector.isIdentical(new SimpleMatrix(values), 1e-5));
    
    // start
    values[0] = new double[]{0.6, 0.7, 0.8, 0.9, 1.0};
    vector = embedding.getStartWordVector();
    vector.reshape(1, 5);
    assertTrue(vector.isIdentical(new SimpleMatrix(values), 1e-5));
    
    // end
    values[0] = new double[]{1, 2, 3, 4, 5};
    vector = embedding.getEndWordVector();
    vector.reshape(1, 5);
    assertTrue(vector.isIdentical(new SimpleMatrix(values), 1e-5));
    
    // word
    values[0] = new double[]{6, 7, 8, 9, 10};
    vector = embedding.get("the");
    vector.reshape(1, 5);
    assertTrue(vector.isIdentical(new SimpleMatrix(values), 1e-5));
  }

  @Test
  public void testLoadFromTwoFile() {
    Embedding embedding = new Embedding(wordFile, vectorFile);
    
    SimpleMatrix vector;
    double[][] values = new double[1][5];
    
    // unknown
    values[0] = new double[]{0.1, 0.2, 0.3, 0.4, 0.5};
    vector = embedding.getUnknownWordVector();
    vector.reshape(1, 5);
    System.err.println(vector);
    assertTrue(vector.isIdentical(new SimpleMatrix(values), 1e-5));
    
    // start
    values[0] = new double[]{0.6, 0.7, 0.8, 0.9, 1.0};
    vector = embedding.getStartWordVector();
    vector.reshape(1, 5);
    assertTrue(vector.isIdentical(new SimpleMatrix(values), 1e-5));
    
    // end
    values[0] = new double[]{1, 2, 3, 4, 5};
    vector = embedding.getEndWordVector();
    vector.reshape(1, 5);
    assertTrue(vector.isIdentical(new SimpleMatrix(values), 1e-5));
    
    // word
    values[0] = new double[]{6, 7, 8, 9, 10};
    vector = embedding.get("the");
    vector.reshape(1, 5);
    assertTrue(vector.isIdentical(new SimpleMatrix(values), 1e-5));
  }

}

