package edu.stanford.nlp.sentiment;

import java.io.Serializable;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.rnn.RNNUtils;
import edu.stanford.nlp.rnn.SimpleTensor;
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
   * 2Nx2NxN, where N is the size of the word vectors
   */
  public TwoDimensionalMap<String, String, SimpleTensor> binaryTensors;

  /**
   * CxN+1, where N = size of word vectors, C is the number of classes
   */
  public TwoDimensionalMap<String, String, SimpleMatrix> binaryClassification;

  /**
   * CxN+1, where N = size of word vectors, C is the number of classes
   */
  public Map<String, SimpleMatrix> unaryClassification;

  public Map<String, SimpleMatrix> wordVectors;

  /**
   * How many classes the RNN is built to test against
   */
  public final int numClasses;

  /**
   * Dimension of hidden layers, size of word vectors, etc
   */
  public final int numHid;

  /**
   * Cached here for easy calculation of the model size;
   * TwoDimensionalMap does not return that in O(1) time
   */
  public final int numBinaryMatrices;

  /** How many elements a transformation matrix has */
  public final int binaryTransformSize;
  /** How many elements the binary transformation tensors have */
  public final int binaryTensorSize;
  /** How many elements a classification matrix has */
  public final int binaryClassificationSize;

  /**
   * Cached here for easy calculation of the model size;
   * TwoDimensionalMap does not return that in O(1) time
   */
  public final int numUnaryMatrices;

  /** How many elements a classification matrix has */
  public final int unaryClassificationSize;

  /**
   * we just keep this here for convenience
   */
  transient SimpleMatrix identity;

  /** 
   * A random number generator - keeping it here lets us reproduce results
   */
  final Random rand;

  static final String UNKNOWN_WORD = "*UNK*";

  /**
   * Will store various options specific to this model
   */
  final RNNOptions op;

  /**
   * Given single matrices and sets of options, create the
   * corresponding SentimentModel.  Useful for creating a Java version
   * of a model trained in some other manner, such as using the
   * original Matlab code.
   */
  static SentimentModel modelFromMatrices(SimpleMatrix W, SimpleMatrix Wcat, SimpleTensor Wt, Map<String, SimpleMatrix> wordVectors, RNNOptions op) {
    if (!op.combineClassification || !op.simplifiedModel) {
      throw new IllegalArgumentException("Can only create a model using this method if combineClassification and simplifiedModel are turned on");
    }
    TwoDimensionalMap<String, String, SimpleMatrix> binaryTransform = TwoDimensionalMap.treeMap();
    binaryTransform.put("", "", W);

    TwoDimensionalMap<String, String, SimpleTensor> binaryTensors = TwoDimensionalMap.treeMap();
    binaryTensors.put("", "", Wt);

    TwoDimensionalMap<String, String, SimpleMatrix> binaryClassification = TwoDimensionalMap.treeMap();

    Map<String, SimpleMatrix> unaryClassification = Generics.newTreeMap();
    unaryClassification.put("", Wcat);

    return new SentimentModel(binaryTransform, binaryTensors, binaryClassification, unaryClassification, wordVectors, op);
  }

  private SentimentModel(TwoDimensionalMap<String, String, SimpleMatrix> binaryTransform,
                         TwoDimensionalMap<String, String, SimpleTensor> binaryTensors,
                         TwoDimensionalMap<String, String, SimpleMatrix> binaryClassification,
                         Map<String, SimpleMatrix> unaryClassification,
                         Map<String, SimpleMatrix> wordVectors,
                         RNNOptions op) {
    this.op = op;

    this.binaryTransform = binaryTransform;
    this.binaryTensors = binaryTensors;
    this.binaryClassification = binaryClassification;
    this.unaryClassification = unaryClassification;
    this.wordVectors = wordVectors;
    this.numClasses = op.numClasses;
    if (op.numHid <= 0) {
      int nh = 0;
      for (SimpleMatrix wv : wordVectors.values()) {
        nh = wv.getNumElements();
      }
      this.numHid = nh;
    } else {
      this.numHid = op.numHid;
    }
    this.numBinaryMatrices = binaryTransform.size();
    binaryTransformSize = numHid * (2 * numHid + 1);
    if (op.useTensors) {
      binaryTensorSize = numHid * numHid * numHid * 4;
    } else {
      binaryTensorSize = 0;
    }
    binaryClassificationSize = (op.combineClassification) ? 0 : numClasses * (numHid + 1);

    numUnaryMatrices = unaryClassification.size();
    unaryClassificationSize = numClasses * (numHid + 1);
    
    rand = new Random(op.randomSeed);

    identity = SimpleMatrix.identity(numHid);
  }

  /**
   * The traditional way of initializing an empty model suitable for training.
   */
  public SentimentModel(RNNOptions op, List<Tree> trainingTrees) {
    this.op = op;
    rand = new Random(op.randomSeed);

    if (op.randomWordVectors) {
      initRandomWordVectors(trainingTrees);
    } else {
      readWordVectors();
    }
    if (op.numHid > 0) {
      this.numHid = op.numHid;
    } else {
      int size = 0;
      for (SimpleMatrix vector : wordVectors.values()) {
        size = vector.getNumElements();
        break;
      }
      this.numHid = size;
    }

    TwoDimensionalSet<String, String> binaryProductions = TwoDimensionalSet.hashSet();
    if (op.simplifiedModel) {
      binaryProductions.add("", "");
    } else {
      // TODO
      // figure out what binary productions we have in these trees
      // Note: the current sentiment training data does not actually
      // have any constituent labels
    }

    Set<String> unaryProductions = Generics.newHashSet();
    if (op.simplifiedModel) {
      unaryProductions.add("");
    } else {
      // TODO
      // figure out what unary productions we have in these trees (preterminals only, after the collapsing)
    }

    this.numClasses = op.numClasses;

    identity = SimpleMatrix.identity(numHid);

    binaryTransform = TwoDimensionalMap.treeMap();
    binaryTensors = TwoDimensionalMap.treeMap();
    binaryClassification = TwoDimensionalMap.treeMap();
    
    // When making a flat model (no symantic untying) the
    // basicCategory function will return the same basic category for
    // all labels, so all entries will map to the same matrix
    for (Pair<String, String> binary : binaryProductions) {
      String left = basicCategory(binary.first);
      String right = basicCategory(binary.second);
      if (binaryTransform.contains(left, right)) {
        continue;
      }
      binaryTransform.put(left, right, randomTransformMatrix());
      if (op.useTensors) {
        binaryTensors.put(left, right, randomBinaryTensor());
      }
      if (!op.combineClassification) {
        binaryClassification.put(left, right, randomClassificationMatrix());
      }
    }
    numBinaryMatrices = binaryTransform.size();
    binaryTransformSize = numHid * (2 * numHid + 1);
    if (op.useTensors) {
      binaryTensorSize = numHid * numHid * numHid * 4;
    } else {
      binaryTensorSize = 0;
    }
    binaryClassificationSize = (op.combineClassification) ? 0 : numClasses * (numHid + 1);

    unaryClassification = Generics.newTreeMap();
    
    // When making a flat model (no symantic untying) the
    // basicCategory function will return the same basic category for
    // all labels, so all entries will map to the same matrix
    for (String unary : unaryProductions) {
      unary = basicCategory(unary);
      if (unaryClassification.containsKey(unary)) {
        continue;
      }
      unaryClassification.put(unary, randomClassificationMatrix());
    }
    numUnaryMatrices = unaryClassification.size();
    unaryClassificationSize = numClasses * (numHid + 1);

    // System.err.println("Binary transform matrices:");
    // System.err.println(binaryTransform);
    // System.err.println("Binary classification matrices:");
    // System.err.println(binaryClassification);
    // System.err.println("Unary classification matrices:");
    // System.err.println(unaryClassification);
  }

  SimpleTensor randomBinaryTensor() {
    double range = 1.0 / (4.0 * numHid);
    SimpleTensor tensor = SimpleTensor.random(numHid * 2, numHid * 2, numHid, -range, range, rand);
    return tensor.scale(op.trainOptions.scalingForInit);
  }
  
  SimpleMatrix randomTransformMatrix() {
    SimpleMatrix binary = new SimpleMatrix(numHid, numHid * 2 + 1);
    // bias column values are initialized zero
    binary.insertIntoThis(0, 0, randomTransformBlock());
    binary.insertIntoThis(0, numHid, randomTransformBlock());
    return binary.scale(op.trainOptions.scalingForInit);
  }

  SimpleMatrix randomTransformBlock() {
    double range = 1.0 / (Math.sqrt((double) numHid) * 2.0);
    return SimpleMatrix.random(numHid,numHid,-range,range,rand).plus(identity);
  }

  /**
   * Returns matrices of the right size for either binary or unary (terminal) classification
   */
  SimpleMatrix randomClassificationMatrix() {
    SimpleMatrix score = new SimpleMatrix(numClasses, numHid + 1);
    // Leave the bias column with 0 values
    double range = 1.0 / (Math.sqrt((double) numHid));
    score.insertIntoThis(0, 0, SimpleMatrix.random(numClasses, numHid, -range, range, rand));
    return score.scale(op.trainOptions.scalingForInit);
  }

  SimpleMatrix randomWordVector() {
    return randomWordVector(op.numHid, rand);
  }

  static SimpleMatrix randomWordVector(int size, Random rand) {
    return RNNUtils.randomGaussian(size, 1, rand);
  }

  void initRandomWordVectors(List<Tree> trainingTrees) {
    if (op.numHid == 0) {
      throw new RuntimeException("Cannot create random word vectors for an unknown numHid");
    }
    Set<String> words = Generics.newHashSet();
    words.add(UNKNOWN_WORD);
    for (Tree tree : trainingTrees) {
      List<Tree> leaves = tree.getLeaves();
      for (Tree leaf : leaves) {
        String word = leaf.label().value();
        if (op.lowercaseWordVectors) {
          word = word.toLowerCase();
        }
        words.add(word);
      }
    }
    this.wordVectors = Generics.newTreeMap();
    for (String word : words) {
      SimpleMatrix vector = randomWordVector();
      wordVectors.put(word, vector);
    }
  }

  void readWordVectors() {
    this.wordVectors = Generics.newTreeMap();
    Map<String, SimpleMatrix> rawWordVectors = RNNUtils.readRawWordVectors(op.wordVectors, op.numHid);
    for (String word : rawWordVectors.keySet()) {
      // TODO: factor out unknown word vector code from DVParser
      wordVectors.put(word, rawWordVectors.get(word));
    }

    String unkWord = op.unkWord;
    SimpleMatrix unknownWordVector = wordVectors.get(unkWord);
    wordVectors.put(UNKNOWN_WORD, unknownWordVector);
    if (unknownWordVector == null) {
      throw new RuntimeException("Unknown word vector not specified in the word vector file");
    }

  }

  public int totalParamSize() {
    int totalSize = 0;
    // binaryTensorSize was set to 0 if useTensors=false
    totalSize = numBinaryMatrices * (binaryTransformSize + binaryClassificationSize + binaryTensorSize);
    totalSize += numUnaryMatrices * unaryClassificationSize;
    totalSize += wordVectors.size() * numHid;
    return totalSize;
  }
  
  public double[] paramsToVector() {
    int totalSize = totalParamSize();
    return RNNUtils.paramsToVector(totalSize, binaryTransform.valueIterator(), binaryClassification.valueIterator(), SimpleTensor.iteratorSimpleMatrix(binaryTensors.valueIterator()), unaryClassification.values().iterator(), wordVectors.values().iterator());
  }

  public void vectorToParams(double[] theta) {
    RNNUtils.vectorToParams(theta, binaryTransform.valueIterator(), binaryClassification.valueIterator(), SimpleTensor.iteratorSimpleMatrix(binaryTensors.valueIterator()), unaryClassification.values().iterator(), wordVectors.values().iterator());
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
      throw new AssertionError("No unary transform matrices, only unary classification");
    } else {
      throw new AssertionError("Unexpected tree children size of " + node.children().length);
    }
  }

  public SimpleTensor getTensorForNode(Tree node) {
    if (!op.useTensors) {
      throw new AssertionError("Not using tensors");
    }
    if (node.children().length == 2) {
      String leftLabel = node.children()[0].value();
      String leftBasic = basicCategory(leftLabel);
      String rightLabel = node.children()[1].value();
      String rightBasic = basicCategory(rightLabel);
      return binaryTensors.get(leftBasic, rightBasic);      
    } else if (node.children().length == 1) {
      throw new AssertionError("No unary transform matrices, only unary classification");
    } else {
      throw new AssertionError("Unexpected tree children size of " + node.children().length);
    }
  }

  public SimpleMatrix getClassWForNode(Tree node) {
    if (op.combineClassification) {
      return unaryClassification.get("");
    } else if (node.children().length == 2) {
      String leftLabel = node.children()[0].value();
      String leftBasic = basicCategory(leftLabel);
      String rightLabel = node.children()[1].value();
      String rightBasic = basicCategory(rightLabel);
      return binaryClassification.get(leftBasic, rightBasic);      
    } else if (node.children().length == 1) {
      String unaryLabel = node.children()[0].value();
      String unaryBasic = basicCategory(unaryLabel);
      return unaryClassification.get(unaryBasic);
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
    return UNKNOWN_WORD;
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

  public SimpleMatrix getUnaryClassification(String category) {
    category = basicCategory(category);
    return unaryClassification.get(category);
  }

  public SimpleMatrix getBinaryClassification(String left, String right) {
    if (op.combineClassification) {
      return unaryClassification.get("");
    } else {
      left = basicCategory(left);
      right = basicCategory(right);
      return binaryClassification.get(left, right);
    }
  }

  public SimpleMatrix getBinaryTransform(String left, String right) {
    left = basicCategory(left);
    right = basicCategory(right);
    return binaryTransform.get(left, right);
  }

  public SimpleTensor getBinaryTensor(String left, String right) {
    left = basicCategory(left);
    right = basicCategory(right);
    return binaryTensors.get(left, right);
  }

  public void saveSerialized(String path) {
    try {
      IOUtils.writeObjectToFile(this, path);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  public static SentimentModel loadSerialized(String path) {
    try {
      return IOUtils.readObjectFromURLOrClasspathOrFileSystem(path);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    } catch (ClassNotFoundException e) {
      throw new RuntimeIOException(e);
    }
  }

  public void printParamInformation(int index) {
    int curIndex = 0;
    for (TwoDimensionalMap.Entry<String, String, SimpleMatrix> entry : binaryTransform) {
      if (curIndex <= index && curIndex + entry.getValue().getNumElements() > index) {
        System.err.println("Index " + index + " is element " + (index - curIndex) + " of binaryTransform \"" + entry.getFirstKey() + "," + entry.getSecondKey() + "\"");
        return;
      } else {
        curIndex += entry.getValue().getNumElements();
      }
    }

    for (TwoDimensionalMap.Entry<String, String, SimpleMatrix> entry : binaryClassification) {
      if (curIndex <= index && curIndex + entry.getValue().getNumElements() > index) {
        System.err.println("Index " + index + " is element " + (index - curIndex) + " of binaryClassification \"" + entry.getFirstKey() + "," + entry.getSecondKey() + "\"");
        return;
      } else {
        curIndex += entry.getValue().getNumElements();
      }
    }

    for (TwoDimensionalMap.Entry<String, String, SimpleTensor> entry : binaryTensors) {
      if (curIndex <= index && curIndex + entry.getValue().getNumElements() > index) {
        System.err.println("Index " + index + " is element " + (index - curIndex) + " of binaryTensor \"" + entry.getFirstKey() + "," + entry.getSecondKey() + "\"");
        return;
      } else {
        curIndex += entry.getValue().getNumElements();
      }
    }

    for (Map.Entry<String, SimpleMatrix> entry : unaryClassification.entrySet()) {
      if (curIndex <= index && curIndex + entry.getValue().getNumElements() > index) {
        System.err.println("Index " + index + " is element " + (index - curIndex) + " of unaryClassification \"" + entry.getKey() + "\"");
        return;
      } else {
        curIndex += entry.getValue().getNumElements();
      }
    }

    for (Map.Entry<String, SimpleMatrix> entry : wordVectors.entrySet()) {
      if (curIndex <= index && curIndex + entry.getValue().getNumElements() > index) {
        System.err.println("Index " + index + " is element " + (index - curIndex) + " of wordVector \"" + entry.getKey() + "\"");
        return;
      } else {
        curIndex += entry.getValue().getNumElements();
      }
    }

    System.err.println("Index " + index + " is beyond the length of the parameters; total parameter space was " + totalParamSize());
  }

  private static final long serialVersionUID = 1;
}
