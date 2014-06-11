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

  public double calculateError() {
    // TODO
    return 0.0;
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

    // stands for Transform Derivatives (see the SentimentModel)
    TwoDimensionalMap<String, String, SimpleMatrix> binaryTD;
    binaryTD = TwoDimensionalMap.treeMap();
    // stands for Classification Derivatives
    TwoDimensionalMap<String, String, SimpleMatrix> binaryCD;
    binaryCD = TwoDimensionalMap.treeMap();

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

    // TODO: left off where we are about to backprop in DVParserCostAndGradient
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
