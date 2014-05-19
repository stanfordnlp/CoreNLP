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

public class EmbeddingITest {
  public static final String wordVectorFile = "edu/stanford/nlp/neural/wordVector.txt";
  public static final String wordFile = "edu/stanford/nlp/neural/word.txt";
  public static final String vectorFile = "edu/stanford/nlp/neural/vector.txt";
  
  @Test
  public void testLoadFromOneFile() {
    Embedding embedding = new Embedding(wordVectorFile);
    
    double[][] values = new double[1][5];
    
    // unknown
    values[0] = new double[]{0.1, 0.2, 0.3, 0.4, 0.5};
    assertTrue(embedding.getUnknownWordVector().transpose().isIdentical(new SimpleMatrix(values), 1e-5));
    
    // start
    values[0] = new double[]{0.6, 0.7, 0.8, 0.9, 1.0};
    assertTrue(embedding.getStartWordVector().transpose().isIdentical(new SimpleMatrix(values), 1e-5));
    
    // end
    values[0] = new double[]{1, 2, 3, 4, 5};
    assertTrue(embedding.getEndWordVector().transpose().isIdentical(new SimpleMatrix(values), 1e-5));
    
    // word
    values[0] = new double[]{6, 7, 8, 9, 10};
    assertTrue(embedding.get("the").transpose().isIdentical(new SimpleMatrix(values), 1e-5));
  }

  @Test
  public void testLoadFromTwoFile() {
    Embedding embedding = new Embedding(wordFile, vectorFile);
    
    double[][] values = new double[1][5];
    
    // unknown
    values[0] = new double[]{0.1, 0.2, 0.3, 0.4, 0.5};
    assertTrue(embedding.getUnknownWordVector().transpose().isIdentical(new SimpleMatrix(values), 1e-5));
    
    // start
    values[0] = new double[]{0.6, 0.7, 0.8, 0.9, 1.0};
    assertTrue(embedding.getStartWordVector().transpose().isIdentical(new SimpleMatrix(values), 1e-5));
    
    // end
    values[0] = new double[]{1, 2, 3, 4, 5};
    assertTrue(embedding.getEndWordVector().transpose().isIdentical(new SimpleMatrix(values), 1e-5));
    
    // word
    values[0] = new double[]{6, 7, 8, 9, 10};
    assertTrue(embedding.get("the").transpose().isIdentical(new SimpleMatrix(values), 1e-5));
  }

}

