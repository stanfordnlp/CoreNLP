package edu.stanford.nlp.sentiment;

import java.util.List;
import java.util.Map;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.optimization.AbstractCachingDiffFunction;
import edu.stanford.nlp.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.rnn.RNNUtils;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.util.TwoDimensionalMap;

// TODO: get rid of the word Sentiment everywhere
public class SentimentCostAndGradient extends AbstractCachingDiffFunction {
  SentimentModel model;
  List<Tree> trainingBatch;

  public SentimentCostAndGradient(SentimentModel model, List<Tree> trainingBatch) {
    this.model = model;
    this.trainingBatch = trainingBatch;
  }

  public int domainDimension() {
    // TODO: cache this for speed?
    return model.totalParamSize();
  }

  public double sumError(Tree tree) {
    if (tree.isLeaf()) {
      return 0.0;
    } else if (tree.isPreTerminal()) {
      return RNNCoreAnnotations.getPredictionError(tree);
    } else {
      double error = 0.0;
      for (Tree child : tree.children()) {
        error += sumError(child);
      }
      return RNNCoreAnnotations.getPredictionError(tree) + error;
    }
  }

  /**
   * Returns the index with the highest value in the <code>predictions</code> matrix.
   * Indexed from 0.
   */
  public int getPredictedClass(SimpleMatrix predictions) {
    int argmax = 0;
    for (int i = 1; i < predictions.getNumElements(); ++i) {
      if (predictions.get(i) > predictions.get(argmax)) {
        argmax = i;
      }
    }
    return argmax;
  }

  public void calculate(double[] theta) {
    model.vectorToParams(theta);

    double localValue = 0.0;
    double[] localDerivative = new double[theta.length];

    // We use TreeMap for each of these so that they stay in a
    // canonical sorted order
    // binaryTD stands for Transform Derivatives (see the SentimentModel)
    TwoDimensionalMap<String, String, SimpleMatrix> binaryTD = TwoDimensionalMap.treeMap();
    // binaryCD stands for Classification Derivatives
    TwoDimensionalMap<String, String, SimpleMatrix> binaryCD = TwoDimensionalMap.treeMap();

    // unaryCD stands for Classification Derivatives
    Map<String, SimpleMatrix> unaryCD = Generics.newTreeMap();

    // word vector derivatives
    Map<String, SimpleMatrix> wordVectorD = Generics.newTreeMap();

    for (TwoDimensionalMap.Entry<String, String, SimpleMatrix> entry : model.binaryTransform) {
      int numRows = entry.getValue().numRows();
      int numCols = entry.getValue().numCols();

      binaryTD.put(entry.getFirstKey(), entry.getSecondKey(), new SimpleMatrix(numRows, numCols));
      // The derivative matrix has one row for each class.  The number
      // of columns in the derivative matrix is the same as the number
      // of rows in the original transform matrix
      binaryCD.put(entry.getFirstKey(), entry.getSecondKey(), new SimpleMatrix(model.numClasses, numRows));
    }
    for (Map.Entry<String, SimpleMatrix> entry : model.unaryClassification.entrySet()) {
      int numRows = entry.getValue().numRows();
      int numCols = entry.getValue().numCols();
      unaryCD.put(entry.getKey(), new SimpleMatrix(numRows, numCols));
    }
    for (Map.Entry<String, SimpleMatrix> entry : model.wordVectors.entrySet()) {
      int numRows = entry.getValue().numRows();
      int numCols = entry.getValue().numCols();
      wordVectorD.put(entry.getKey(), new SimpleMatrix(numRows, numCols));
    }
    
    // TODO: This part can easily be parallelized
    List<Tree> forwardPropTrees = Generics.newArrayList();
    for (Tree tree : trainingBatch) {
      Tree trainingTree = tree.deepCopy();
      // this will attach the error vectors and the node vectors
      // to each node in the tree
      forwardPropagateTree(trainingTree);
      forwardPropTrees.add(trainingTree);
    }

    // TODO: we may find a big speedup by separating the derivatives and then summing
    double error = 0.0;
    for (Tree tree : forwardPropTrees) {
      backpropDerivativesAndError(tree, binaryTD, binaryCD, unaryCD, wordVectorD);
      error += sumError(tree);
    }

    value = error;
    derivative = RNNUtils.paramsToVector(theta.length, binaryTD.valueIterator(), binaryCD.valueIterator(), unaryCD.values().iterator(), wordVectorD.values().iterator());
  }

  private void backpropDerivativesAndError(Tree tree, 
                                           TwoDimensionalMap<String, String, SimpleMatrix> binaryTD,
                                           TwoDimensionalMap<String, String, SimpleMatrix> binaryCD,
                                           Map<String, SimpleMatrix> unaryCD,
                                           Map<String, SimpleMatrix> wordVectorD) {
    SimpleMatrix delta = new SimpleMatrix(model.op.numHid, 1);
    backpropDerivativesAndError(tree, binaryTD, binaryCD, unaryCD, wordVectorD, delta);
  }

  private void backpropDerivativesAndError(Tree tree, 
                                           TwoDimensionalMap<String, String, SimpleMatrix> binaryTD,
                                           TwoDimensionalMap<String, String, SimpleMatrix> binaryCD,
                                           Map<String, SimpleMatrix> unaryCD,
                                           Map<String, SimpleMatrix> wordVectorD,
                                           SimpleMatrix deltaUp) {
    if (tree.isLeaf()) {
      return;
    }

    SimpleMatrix currentVector = RNNCoreAnnotations.getNodeVector(tree);
    String category = tree.label().value();
    category = model.basicCategory(category);

    // TODO: factor this out somewhere?
    SimpleMatrix goldLabel = new SimpleMatrix(model.numClasses, 1);
    goldLabel.set(RNNCoreAnnotations.getGoldClass(tree), 1.0);

    SimpleMatrix predictions = RNNCoreAnnotations.getPredictions(tree);

    SimpleMatrix deltaClass = predictions.minus(goldLabel);
    SimpleMatrix localCD = deltaClass.mult(RNNUtils.concatenateWithBias(currentVector.transpose()));

    double error = -(RNNUtils.elementwiseApplyLog(predictions).elementMult(goldLabel).elementSum());
    RNNCoreAnnotations.setPredictionError(tree, error);

    if (tree.isPreTerminal()) { // below us is a word vector
      unaryCD.put(category, unaryCD.get(category).plus(localCD));

      String word = tree.children()[0].label().value();
      word = model.getVocabWord(word);

      SimpleMatrix deltaFromClass = model.getUnaryClassification(category).transpose().mult(deltaClass);
      SimpleMatrix deltaFull = deltaFromClass.plus(deltaUp);

      SimpleMatrix currentVectorDerivative = RNNUtils.elementwiseApplyTanhDerivative(currentVector);
      SimpleMatrix wordDerivative = deltaFull.elementMult(currentVectorDerivative);
      wordVectorD.put(word, wordVectorD.get(word).plus(wordDerivative));
    } else {
      // Otherwise, this must be a binary node
      String leftCategory = model.basicCategory(tree.children()[0].label().value());
      String rightCategory = model.basicCategory(tree.children()[1].label().value());
      binaryCD.put(leftCategory, rightCategory, binaryCD.get(leftCategory, rightCategory).plus(localCD));
      
      SimpleMatrix deltaFromClass = model.getBinaryClassification(leftCategory, rightCategory).transpose().mult(deltaClass);
      SimpleMatrix deltaFull = deltaFromClass.plus(deltaUp);
      
      SimpleMatrix leftVector = RNNCoreAnnotations.getNodeVector(tree.children()[0]);
      SimpleMatrix rightVector = RNNCoreAnnotations.getNodeVector(tree.children()[1]);
      SimpleMatrix childrenVector = RNNUtils.concatenateWithBias(leftVector, rightVector);
      SimpleMatrix W_df = deltaFull.mult(childrenVector.transpose());
      binaryTD.put(leftCategory, rightCategory, binaryTD.get(leftCategory, rightCategory).plus(W_df));
      
      SimpleMatrix leftDerivative = RNNUtils.elementwiseApplyTanhDerivative(leftVector);
      SimpleMatrix rightDerivative = RNNUtils.elementwiseApplyTanhDerivative(rightVector);
      SimpleMatrix leftWTDelta = deltaFromClass.extractMatrix(0, deltaFull.numRows(), 0, 1);  // TODO: is this correct? both use of deltaFromClass and deltaFull
      SimpleMatrix rightWTDelta = deltaFromClass.extractMatrix(deltaFull.numRows(), deltaFull.numRows() * 2, 0, 1);
      backpropDerivativesAndError(tree.children()[0], binaryTD, binaryCD, unaryCD, wordVectorD, leftDerivative.elementMult(leftWTDelta));
      backpropDerivativesAndError(tree.children()[1], binaryTD, binaryCD, unaryCD, wordVectorD, rightDerivative.elementMult(rightWTDelta));
    }
  }


  private void forwardPropagateTree(Tree tree) {
    SimpleMatrix nodeVector = null;
    SimpleMatrix classification = null;

    if (tree.isLeaf()) {
      // We do nothing for the leaves.  The preterminals will
      // calculate the classification for this word/tag.  In fact, the
      // recursion should not have gotten here (unless there are
      // degenerate trees of just one leaf)
      throw new AssertionError("We should not have reached leaves in forwardPropagate");
    } else if (tree.isPreTerminal()) {
      classification = model.getUnaryClassification(tree.label().value());
      String word = tree.children()[0].label().value();
      SimpleMatrix wordVector = model.getWordVector(word);
      nodeVector = RNNUtils.elementwiseApplyTanh(wordVector);
    } else if (tree.children().length == 1) {
      throw new AssertionError("Non-preterminal nodes of size 1 should have already been collapsed");
    } else if (tree.children().length == 2) {
      forwardPropagateTree(tree.children()[0]);
      forwardPropagateTree(tree.children()[1]);

      String leftCategory = tree.children()[0].label().value();
      String rightCategory = tree.children()[1].label().value();
      SimpleMatrix W = model.getBinaryTransform(leftCategory, rightCategory);
      classification = model.getBinaryClassification(leftCategory, rightCategory);

      SimpleMatrix leftVector = RNNCoreAnnotations.getNodeVector(tree.children()[0]);
      SimpleMatrix rightVector = RNNCoreAnnotations.getNodeVector(tree.children()[1]);
      SimpleMatrix childrenVector = RNNUtils.concatenateWithBias(leftVector, rightVector);
      nodeVector = RNNUtils.elementwiseApplyTanh(W.mult(childrenVector));
    } else {
      throw new AssertionError("Tree not correctly binarized");
    }

    SimpleMatrix predictions = RNNUtils.softmax(classification.mult(nodeVector));

    int index = getPredictedClass(predictions);
    if (!(tree.label() instanceof CoreLabel)) {
      throw new AssertionError("Expected CoreLabels in the nodes");
    }
    CoreLabel label = (CoreLabel) tree.label();
    label.set(RNNCoreAnnotations.Predictions.class, predictions);
    label.set(RNNCoreAnnotations.PredictedClass.class, index);
    label.set(RNNCoreAnnotations.NodeVector.class, nodeVector);
  }
}
