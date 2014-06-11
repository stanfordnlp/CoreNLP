package edu.stanford.nlp.sentiment;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.rnn.RNNUtils;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.TwoDimensionalMap;
import edu.stanford.nlp.util.TwoDimensionalSet;

public class SentimentModel implements Serializable {
  /**
   * Nx2N+1, where N is the size of the word vectors
   */
  public TwoDimensionalMap<String, String, SimpleMatrix> binaryTransform;

  /**
   * CxN+1, where N = size of word vectors, C is the number of classes
   */
  public TwoDimensionalMap<String, String, SimpleMatrix> binaryClassification;

  public Map<String, SimpleMatrix> wordVectors;

  /**
   * TODO: obviously this will depend on the data set, not always be 5
   */
  public final int numClasses = 5;

  /**
   * Dimension of hidden layers, size of word vectors, etc
   */
  public final int numHid;

  /**
   * Cached here for easy calculation of the model size;
   * TwoDimensionalMap does not return that in O(1) time
   */
  public final int numBinaryMatrices;

  public final int binaryTransformSize;
  public final int binaryClassificationSize;

  /**
   * we just keep this here for convenience
   */
  transient SimpleMatrix identity;

  /** 
   * A random number generator - keeping it here lets us reproduce results
   */
  Random rand;


  /**
   * Will store various options specific to this model
   */
  Options op;
  
  static final String UNKNOWN_WORD = "*UNK*";
  
  public SentimentModel(Options op, TwoDimensionalSet<String, String> binaryProductions) {
    this.op = op;
    rand = (op.randomSeed != 0) ? new Random(op.randomSeed) : new Random(); 

    // TODO: extract this from the word vector file
    this.numHid = op.numHid;
    readWordVectors();
    identity = SimpleMatrix.identity(numHid);

    binaryTransform = TwoDimensionalMap.treeMap();
    binaryClassification = TwoDimensionalMap.treeMap();
    
    // When making a flat model (no symantic untying) the
    // basicCategory function will return the same basic category for
    // all labels, so all entries will map to the same matrix
    for (Pair<String, String> binary : binaryProductions) {
      String left = basicCategory(binary.first);
      String right = basicCategory(binary.second);
      if (binaryTransform.get(left, right) == null) {
        continue;
      }
      binaryTransform.put(left, right, randomTransformMatrix());
      binaryClassification.put(left, right, randomClassificationMatrix());
    }
    numBinaryMatrices = binaryTransform.size();
    binaryTransformSize = numHid * (2 * numHid + 1);
    binaryClassificationSize = numClasses * (numHid + 1);
  }
  
  SimpleMatrix randomTransformMatrix() {
    SimpleMatrix binary = new SimpleMatrix(numHid, numHid * 2 + 1);
    // bias column values are initialized zero
    binary.insertIntoThis(0, 0, randomTransformBlock());
    binary.insertIntoThis(0, numHid, randomTransformBlock());
    return binary.scale(op.scalingForInit);
  }

  SimpleMatrix randomTransformBlock() {
    return SimpleMatrix.random(numHid,numHid,-1.0/Math.sqrt((double)numHid * 100.0),1.0/Math.sqrt((double)numHid * 100.0),rand).plus(identity);
  }

  SimpleMatrix randomClassificationMatrix() {
    SimpleMatrix score = new SimpleMatrix(numClasses, numHid + 1);
    // Leave the bias column with 0 values
    score.insertIntoThis(0, 0, SimpleMatrix.random(numClasses, numHid, -1.0/Math.sqrt((double)numHid),1.0/Math.sqrt((double)numHid),rand));
    return score.scale(op.scalingForInit);
  }

  void readWordVectors() {
    // TODO: this needs to be factored out from DVModel
  }

  public int totalParamSize() {
    int totalSize = 0;
    totalSize = numBinaryMatrices * (binaryTransformSize + binaryClassificationSize);
    totalSize += wordVectors.size() * numHid;
    return totalSize;
  }
  
  public double[] paramsToVector() {
    int totalSize = totalParamSize();
    return RNNUtils.paramsToVector(totalSize, binaryTransform.valueIterator(), binaryClassification.valueIterator(), wordVectors.values().iterator());
  }

  public void vectorToParams(double[] theta) {
    RNNUtils.vectorToParams(theta, binaryTransform.valueIterator(), binaryClassification.valueIterator(), wordVectors.values().iterator());
  }

  // TODO: combine this and getClassWForNode?
  public SimpleMatrix getWForNode(Tree node) {
    if (node.children().length == 2) {
      String leftLabel = node.children()[0].value();
      String leftBasic = basicCategory(leftLabel);
      String rightLabel = node.children()[1].value();
      String rightBasic = basicCategory(rightLabel);
      return binaryTransform.get(leftBasic, rightBasic);      
    } else if (node.children().length == 1) {
      throw new AssertionError("Unary nodes should have been skipped");
    } else {
      throw new AssertionError("Unexpected tree children size of " + node.children().length);
    }
  }

  public SimpleMatrix getClassWForNode(Tree node) {
    if (node.children().length == 2) {
      String leftLabel = node.children()[0].value();
      String leftBasic = basicCategory(leftLabel);
      String rightLabel = node.children()[1].value();
      String rightBasic = basicCategory(rightLabel);
      return binaryClassification.get(leftBasic, rightBasic);      
    } else if (node.children().length == 1) {
      throw new AssertionError("Unary nodes should have been skipped");
    } else {
      throw new AssertionError("Unexpected tree children size of " + node.children().length);
    }
  }

  public SimpleMatrix getWordVector(String word) {
    return wordVectors.get(getVocabWord(word));
  }

  public String getVocabWord(String word) {
    if (op.lowercaseWordVectors) {
      word = word.toLowerCase();
    }
    if (wordVectors.containsKey(word)) {
      return word;
    }
    // TODO: go through unknown words here
    return null;
  }

  public String basicCategory(String category) {
    if (op.simplifiedModel) {
      return "";
    }
    String basic = op.langpack.basicCategory(category);
    if (basic.length() > 0 && basic.charAt(0) == '@') {
      basic = basic.substring(1);
    }
    return basic;
  }

  private static final long serialVersionUID = 1;
}
