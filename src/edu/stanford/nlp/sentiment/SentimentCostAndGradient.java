package edu.stanford.nlp.sentiment;

import java.util.List;
import java.util.Map;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.optimization.AbstractCachingDiffFunction;
import edu.stanford.nlp.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.rnn.RNNUtils;
import edu.stanford.nlp.rnn.SimpleTensor;
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
    // TODO: factor out the initialization routines
    // binaryTD stands for Transform Derivatives (see the SentimentModel)
    TwoDimensionalMap<String, String, SimpleMatrix> binaryTD = TwoDimensionalMap.treeMap();
    // the derivatives of the tensors for the binary nodes
    TwoDimensionalMap<String, String, SimpleTensor> binaryTensorTD = TwoDimensionalMap.treeMap();
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
    }

    if (!model.op.combineClassification) {
      for (TwoDimensionalMap.Entry<String, String, SimpleMatrix> entry : model.binaryClassification) {
        int numRows = entry.getValue().numRows();
        int numCols = entry.getValue().numCols();
        
        binaryCD.put(entry.getFirstKey(), entry.getSecondKey(), new SimpleMatrix(numRows, numCols));
      }
    }

    if (model.op.useTensors) {
      for (TwoDimensionalMap.Entry<String, String, SimpleTensor> entry : model.binaryTensors) {
        int numRows = entry.getValue().numRows();
        int numCols = entry.getValue().numCols();
        int numSlices = entry.getValue().numSlices();
        
        binaryTensorTD.put(entry.getFirstKey(), entry.getSecondKey(), new SimpleTensor(numRows, numCols, numSlices));
      }
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
      backpropDerivativesAndError(tree, binaryTD, binaryCD, binaryTensorTD, unaryCD, wordVectorD);
      error += sumError(tree);
    }

    // scale the error by the number of sentences so that the
    // regularization isn't drowned out for large training batchs
    double scale = (1.0 / trainingBatch.size());
    value = error * scale;

    value += scaleAndRegularize(binaryTD, model.binaryTransform, scale, model.op.trainOptions.regTransform);
    value += scaleAndRegularize(binaryCD, model.binaryClassification, scale, model.op.trainOptions.regClassification);
    value += scaleAndRegularizeTensor(binaryTensorTD, model.binaryTensors, scale, model.op.trainOptions.regTransform);
    value += scaleAndRegularize(unaryCD, model.unaryClassification, scale, model.op.trainOptions.regClassification);
    value += scaleAndRegularize(wordVectorD, model.wordVectors, scale, model.op.trainOptions.regWordVector);

    derivative = RNNUtils.paramsToVector(theta.length, binaryTD.valueIterator(), binaryCD.valueIterator(), SimpleTensor.iteratorSimpleMatrix(binaryTensorTD.valueIterator()), unaryCD.values().iterator(), wordVectorD.values().iterator());
  }

  double scaleAndRegularize(TwoDimensionalMap<String, String, SimpleMatrix> derivatives,
                            TwoDimensionalMap<String, String, SimpleMatrix> currentMatrices,
                            double scale,
                            double regCost) {
    double cost = 0.0; // the regularization cost
    for (TwoDimensionalMap.Entry<String, String, SimpleMatrix> entry : currentMatrices) {
      SimpleMatrix D = derivatives.get(entry.getFirstKey(), entry.getSecondKey());
      D = D.scale(scale).plus(entry.getValue().scale(regCost));
      derivatives.put(entry.getFirstKey(), entry.getSecondKey(), D);
      cost += entry.getValue().elementMult(entry.getValue()).elementSum() * regCost / 2.0;
    }
    return cost;
  }

  double scaleAndRegularize(Map<String, SimpleMatrix> derivatives,
                            Map<String, SimpleMatrix> currentMatrices,
                            double scale,
                            double regCost) {
    double cost = 0.0; // the regularization cost
    for (Map.Entry<String, SimpleMatrix> entry : currentMatrices.entrySet()) {
      SimpleMatrix D = derivatives.get(entry.getKey());
      D = D.scale(scale).plus(entry.getValue().scale(regCost));
      derivatives.put(entry.getKey(), D);
      cost += entry.getValue().elementMult(entry.getValue()).elementSum() * regCost / 2.0;
    }
    return cost;
  }

  double scaleAndRegularizeTensor(TwoDimensionalMap<String, String, SimpleTensor> derivatives,
                                  TwoDimensionalMap<String, String, SimpleTensor> currentMatrices,
                                  double scale,
                                  double regCost) {
    double cost = 0.0; // the regularization cost
    for (TwoDimensionalMap.Entry<String, String, SimpleTensor> entry : currentMatrices) {
      SimpleTensor D = derivatives.get(entry.getFirstKey(), entry.getSecondKey());
      D = D.scale(scale).plus(entry.getValue().scale(regCost));
      derivatives.put(entry.getFirstKey(), entry.getSecondKey(), D);
      cost += entry.getValue().elementMult(entry.getValue()).elementSum() * regCost / 2.0;
    }
    return cost;
  }

  private void backpropDerivativesAndError(Tree tree, 
                                           TwoDimensionalMap<String, String, SimpleMatrix> binaryTD,
                                           TwoDimensionalMap<String, String, SimpleMatrix> binaryCD,
                                           TwoDimensionalMap<String, String, SimpleTensor> binaryTensorTD,
                                           Map<String, SimpleMatrix> unaryCD,
                                           Map<String, SimpleMatrix> wordVectorD) {
    SimpleMatrix delta = new SimpleMatrix(model.op.numHid, 1);
    backpropDerivativesAndError(tree, binaryTD, binaryCD, binaryTensorTD, unaryCD, wordVectorD, delta);
  }

  private void backpropDerivativesAndError(Tree tree, 
                                           TwoDimensionalMap<String, String, SimpleMatrix> binaryTD,
                                           TwoDimensionalMap<String, String, SimpleMatrix> binaryCD,
                                           TwoDimensionalMap<String, String, SimpleTensor> binaryTensorTD,
                                           Map<String, SimpleMatrix> unaryCD,
                                           Map<String, SimpleMatrix> wordVectorD,
                                           SimpleMatrix deltaUp) {
    if (tree.isLeaf()) {
      return;
    }

    SimpleMatrix currentVector = RNNCoreAnnotations.getNodeVector(tree);
    String category = tree.label().value();
    category = model.basicCategory(category);

    // Build a vector that looks like 0,0,1,0,0 with an indicator for the correct class
    SimpleMatrix goldLabel = new SimpleMatrix(model.numClasses, 1);
    int goldClass = RNNCoreAnnotations.getGoldClass(tree);
    goldLabel.set(goldClass, 1.0);

    double nodeWeight = model.op.trainOptions.getClassWeight(goldClass);

    SimpleMatrix predictions = RNNCoreAnnotations.getPredictions(tree);

    SimpleMatrix deltaClass = predictions.minus(goldLabel).scale(nodeWeight);
    SimpleMatrix localCD = deltaClass.mult(RNNUtils.concatenateWithBias(currentVector).transpose());

    double error = -(RNNUtils.elementwiseApplyLog(predictions).elementMult(goldLabel).elementSum());
    error = error * nodeWeight;
    RNNCoreAnnotations.setPredictionError(tree, error);

    if (tree.isPreTerminal()) { // below us is a word vector
      unaryCD.put(category, unaryCD.get(category).plus(localCD));

      String word = tree.children()[0].label().value();
      word = model.getVocabWord(word);

      //SimpleMatrix currentVectorDerivative = RNNUtils.elementwiseApplyTanhDerivative(currentVector);
      //SimpleMatrix deltaFromClass = model.getUnaryClassification(category).transpose().mult(deltaClass);
      //SimpleMatrix deltaFull = deltaFromClass.extractMatrix(0, model.op.numHid, 0, 1).plus(deltaUp);
      //SimpleMatrix wordDerivative = deltaFull.elementMult(currentVectorDerivative);
      //wordVectorD.put(word, wordVectorD.get(word).plus(wordDerivative));

      SimpleMatrix currentVectorDerivative = RNNUtils.elementwiseApplyTanhDerivative(currentVector);
      SimpleMatrix deltaFromClass = model.getUnaryClassification(category).transpose().mult(deltaClass);
      deltaFromClass = deltaFromClass.extractMatrix(0, model.op.numHid, 0, 1).elementMult(currentVectorDerivative);
      SimpleMatrix deltaFull = deltaFromClass.plus(deltaUp);
      wordVectorD.put(word, wordVectorD.get(word).plus(deltaFull));
    } else {
      // Otherwise, this must be a binary node
      String leftCategory = model.basicCategory(tree.children()[0].label().value());
      String rightCategory = model.basicCategory(tree.children()[1].label().value());
      if (model.op.combineClassification) {
        unaryCD.put("", unaryCD.get("").plus(localCD));
      } else {
        binaryCD.put(leftCategory, rightCategory, binaryCD.get(leftCategory, rightCategory).plus(localCD));
      }
      
      SimpleMatrix currentVectorDerivative = RNNUtils.elementwiseApplyTanhDerivative(currentVector);
      SimpleMatrix deltaFromClass = model.getBinaryClassification(leftCategory, rightCategory).transpose().mult(deltaClass);
      deltaFromClass = deltaFromClass.extractMatrix(0, model.op.numHid, 0, 1).elementMult(currentVectorDerivative);
      SimpleMatrix deltaFull = deltaFromClass.plus(deltaUp);
      
      SimpleMatrix leftVector = RNNCoreAnnotations.getNodeVector(tree.children()[0]);
      SimpleMatrix rightVector = RNNCoreAnnotations.getNodeVector(tree.children()[1]);
      SimpleMatrix childrenVector = RNNUtils.concatenateWithBias(leftVector, rightVector);
      SimpleMatrix W_df = deltaFull.mult(childrenVector.transpose());
      binaryTD.put(leftCategory, rightCategory, binaryTD.get(leftCategory, rightCategory).plus(W_df));
      SimpleMatrix deltaDown;
      if (model.op.useTensors) {
        SimpleTensor Wt_df = getTensorGradient(deltaFull, leftVector, rightVector);
        binaryTensorTD.put(leftCategory, rightCategory, binaryTensorTD.get(leftCategory, rightCategory).plus(Wt_df));
        deltaDown = computeTensorDeltaDown(deltaFull, leftVector, rightVector, model.getBinaryTransform(leftCategory, rightCategory), model.getBinaryTensor(leftCategory, rightCategory));
      } else {
        deltaDown = model.getBinaryTransform(leftCategory, rightCategory).transpose().mult(deltaFull);
      }

      SimpleMatrix leftDerivative = RNNUtils.elementwiseApplyTanhDerivative(leftVector);
      SimpleMatrix rightDerivative = RNNUtils.elementwiseApplyTanhDerivative(rightVector);
      SimpleMatrix leftDeltaDown = deltaDown.extractMatrix(0, deltaFull.numRows(), 0, 1);
      SimpleMatrix rightDeltaDown = deltaDown.extractMatrix(deltaFull.numRows(), deltaFull.numRows() * 2, 0, 1);
      backpropDerivativesAndError(tree.children()[0], binaryTD, binaryCD, binaryTensorTD, unaryCD, wordVectorD, leftDerivative.elementMult(leftDeltaDown));
      backpropDerivativesAndError(tree.children()[1], binaryTD, binaryCD, binaryTensorTD, unaryCD, wordVectorD, rightDerivative.elementMult(rightDeltaDown));
    }
  }

  private SimpleMatrix computeTensorDeltaDown(SimpleMatrix deltaFull, SimpleMatrix leftVector, SimpleMatrix rightVector,
                                              SimpleMatrix W, SimpleTensor Wt) {
    SimpleMatrix WTDelta = W.transpose().mult(deltaFull);
    SimpleMatrix WTDeltaNoBias = WTDelta.extractMatrix(0, deltaFull.numRows() * 2, 0, 1);
    int size = deltaFull.getNumElements();
    SimpleMatrix deltaTensor = new SimpleMatrix(size*2, 1);
    SimpleMatrix fullVector = RNNUtils.concatenate(leftVector, rightVector);
    for (int slice = 0; slice < size; ++slice) {
      SimpleMatrix scaledFullVector = fullVector.scale(deltaFull.get(slice));
      deltaTensor = deltaTensor.plus(Wt.getSlice(slice).plus(Wt.getSlice(slice).transpose()).mult(scaledFullVector));
    }
    return deltaTensor.plus(WTDeltaNoBias);
  }

  private SimpleTensor getTensorGradient(SimpleMatrix deltaFull, SimpleMatrix leftVector, SimpleMatrix rightVector) {
    int size = deltaFull.getNumElements();
    SimpleTensor Wt_df = new SimpleTensor(size*2, size*2, size);
    // TODO: combine this concatenation with computeTensorDeltaDown?
    SimpleMatrix fullVector = RNNUtils.concatenate(leftVector, rightVector);
    for (int slice = 0; slice < size; ++slice) {
      Wt_df.setSlice(slice, fullVector.scale(deltaFull.get(slice)).mult(fullVector.transpose()));
    }
    return Wt_df;
  }

  /**
   * This is the method to call for assigning labels and node vectors
   * to the Tree.  After calling this, each of the non-leaf nodes will
   * have the node vector and the predictions of their classes
   * assigned to that subtree's node.  The annotations filled in are
   * the RNNCoreAnnotations.NodeVector, Predictions, and
   * PredictedClass.  In general, PredictedClass will be the most
   * useful annotation except when training.
   */
  public void forwardPropagateTree(Tree tree) {
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
      if (model.op.useTensors) {
        SimpleTensor tensor = model.getBinaryTensor(leftCategory, rightCategory);
        SimpleMatrix tensorIn = RNNUtils.concatenate(leftVector, rightVector);
        SimpleMatrix tensorOut = tensor.bilinearProducts(tensorIn);        
        nodeVector = RNNUtils.elementwiseApplyTanh(W.mult(childrenVector).plus(tensorOut));
      } else {
        nodeVector = RNNUtils.elementwiseApplyTanh(W.mult(childrenVector));
      }
    } else {
      throw new AssertionError("Tree not correctly binarized");
    }

    SimpleMatrix predictions = RNNUtils.softmax(classification.mult(RNNUtils.concatenateWithBias(nodeVector)));

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
