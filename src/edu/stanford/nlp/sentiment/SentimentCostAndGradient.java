package edu.stanford.nlp.sentiment;

import java.util.List;
import java.util.Map;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.optimization.AbstractCachingDiffFunction;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.util.TwoDimensionalMap;

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
    
    // TODO: Here is where we forward propogate and get the error for
    // each tree
    
    for (Tree tree : trainingBatch) {
      // TODO: this will attach the error vectors and the node vectors
      // to each node in the tree
      Tree trainingTree = tree.deepCopy();
      forwardPropagateTree(trainingTree);
    }

    // TODO: left off at the ScoringProcessor in DVParserCostAndGradient
  }

  private void forwardPropagateTree(Tree tree) {
    if (tree.isLeaf()) {
      // TODO: here we compute the predictions of the leaf
      // (need to add the necessary components for unary
      // classification to the SentimentModel)
    }
  }
}
