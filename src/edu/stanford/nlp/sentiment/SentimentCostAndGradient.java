package edu.stanford.nlp.sentiment;

import java.util.List;
import java.util.Map;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.neural.NeuralUtils;
import edu.stanford.nlp.neural.SimpleTensor;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.optimization.AbstractCachingDiffFunction;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.TwoDimensionalMap;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;
import edu.stanford.nlp.util.logging.Redwood;

// TODO: get rid of the word Sentiment everywhere
public class SentimentCostAndGradient extends AbstractCachingDiffFunction {

  private static final Redwood.RedwoodChannels log = Redwood.channels(SentimentCostAndGradient.class);

  private final SentimentModel model;
  private final List<Tree> trainingBatch;

  public SentimentCostAndGradient(SentimentModel model, List<Tree> trainingBatch) {
    this.model = model;
    this.trainingBatch = trainingBatch;
  }

  @Override
  public int domainDimension() {
    // TODO: cache this for speed?
    return model.totalParamSize();
  }

  private static double sumError(Tree tree) {
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
   * Returns the index with the highest value in the {@code predictions} matrix.
   * Indexed from 0.
   */
  private static int getPredictedClass(SimpleMatrix predictions) {
    int argmax = 0;
    for (int i = 1; i < predictions.getNumElements(); ++i) {
      if (predictions.get(i) > predictions.get(argmax)) {
        argmax = i;
      }
    }
    return argmax;
  }

  private static class ModelDerivatives {
    // We use TreeMap for each of these so that they stay in a canonical sorted order
    // binaryTD stands for Transform Derivatives (see the SentimentModel)
    public final TwoDimensionalMap<String, String, SimpleMatrix> binaryTD;
    // the derivatives of the tensors for the binary nodes
    // will be empty if we aren't using tensors
    public final TwoDimensionalMap<String, String, SimpleTensor> binaryTensorTD;
    // binaryCD stands for Classification Derivatives
    // if we combined classification derivatives, we just use an empty map
    public final TwoDimensionalMap<String, String, SimpleMatrix> binaryCD;

    // unaryCD stands for Classification Derivatives
    public final Map<String, SimpleMatrix> unaryCD;

    // word vector derivatives
    // will be filled on an as-needed basis, as opposed to having all
    // the words with a lot of empty vectors
    public final Map<String, SimpleMatrix> wordVectorD;

    public double error = 0.0;

    public ModelDerivatives(SentimentModel model) {
      binaryTD = initDerivatives(model.binaryTransform);
      binaryTensorTD = (model.op.useTensors) ? initTensorDerivatives(model.binaryTensors) : TwoDimensionalMap.treeMap();
      binaryCD = (!model.op.combineClassification) ? initDerivatives(model.binaryClassification) : TwoDimensionalMap.treeMap();
      unaryCD = initDerivatives(model.unaryClassification);
      // wordVectorD will be filled on an as-needed basis
      wordVectorD = Generics.newTreeMap();
    }

    public void add(ModelDerivatives other) {
      addMatrices(binaryTD, other.binaryTD);
      addTensors(binaryTensorTD, other.binaryTensorTD);
      addMatrices(binaryCD, other.binaryCD);
      addMatrices(unaryCD, other.unaryCD);
      addMatrices(wordVectorD, other.wordVectorD);

      error += other.error;
    }

    /**
     * Add matrices from the second map to the first map, in place.
     */
    public static void addMatrices(TwoDimensionalMap<String, String, SimpleMatrix> first,
                                   TwoDimensionalMap<String, String, SimpleMatrix> second) {
      for (TwoDimensionalMap.Entry<String, String, SimpleMatrix> entry : first) {
        if (second.contains(entry.getFirstKey(), entry.getSecondKey())) {
          first.put(entry.getFirstKey(), entry.getSecondKey(), entry.getValue().plus(second.get(entry.getFirstKey(), entry.getSecondKey())));
        }
      }
      for (TwoDimensionalMap.Entry<String, String, SimpleMatrix> entry : second) {
        if (!first.contains(entry.getFirstKey(), entry.getSecondKey())) {
          first.put(entry.getFirstKey(), entry.getSecondKey(), entry.getValue());
        }
      }
    }

    /**
     * Add tensors from the second map to the first map, in place.
     */
    public static void addTensors(TwoDimensionalMap<String, String, SimpleTensor> first,
                                  TwoDimensionalMap<String, String, SimpleTensor> second) {
      for (TwoDimensionalMap.Entry<String, String, SimpleTensor> entry : first) {
        if (second.contains(entry.getFirstKey(), entry.getSecondKey())) {
          first.put(entry.getFirstKey(), entry.getSecondKey(), entry.getValue().plus(second.get(entry.getFirstKey(), entry.getSecondKey())));
        }
      }
      for (TwoDimensionalMap.Entry<String, String, SimpleTensor> entry : second) {
        if (!first.contains(entry.getFirstKey(), entry.getSecondKey())) {
          first.put(entry.getFirstKey(), entry.getSecondKey(), entry.getValue());
        }
      }
    }

    /**
     * Add matrices from the second map to the first map, in place.
     */
    public static void addMatrices(Map<String, SimpleMatrix> first,
                                   Map<String, SimpleMatrix> second) {
      for (Map.Entry<String, SimpleMatrix> entry : first.entrySet()) {
        if (second.containsKey(entry.getKey())) {
          first.put(entry.getKey(), entry.getValue().plus(second.get(entry.getKey())));
        }
      }
      for (Map.Entry<String, SimpleMatrix> entry : second.entrySet()) {
        if (!first.containsKey(entry.getKey())) {
          first.put(entry.getKey(), entry.getValue());
        }
      }
    }


    /**
     * Init a TwoDimensionalMap with 0 matrices for all the matrices in the original map.
     */
    private static TwoDimensionalMap<String, String, SimpleMatrix> initDerivatives(TwoDimensionalMap<String, String, SimpleMatrix> map) {
      TwoDimensionalMap<String, String, SimpleMatrix> derivatives = TwoDimensionalMap.treeMap();

      for (TwoDimensionalMap.Entry<String, String, SimpleMatrix> entry : map) {
        int numRows = entry.getValue().numRows();
        int numCols = entry.getValue().numCols();

        derivatives.put(entry.getFirstKey(), entry.getSecondKey(), new SimpleMatrix(numRows, numCols));
      }

      return derivatives;
    }

    /**
     * Init a TwoDimensionalMap with 0 tensors for all the tensors in the original map.
     */
    private static TwoDimensionalMap<String, String, SimpleTensor> initTensorDerivatives(TwoDimensionalMap<String, String, SimpleTensor> map) {
      TwoDimensionalMap<String, String, SimpleTensor> derivatives = TwoDimensionalMap.treeMap();

      for (TwoDimensionalMap.Entry<String, String, SimpleTensor> entry : map) {
        int numRows = entry.getValue().numRows();
        int numCols = entry.getValue().numCols();
        int numSlices = entry.getValue().numSlices();

        derivatives.put(entry.getFirstKey(), entry.getSecondKey(), new SimpleTensor(numRows, numCols, numSlices));
      }

      return derivatives;
    }

    /**
     * Init a Map with 0 matrices for all the matrices in the original map.
     */
    private static Map<String, SimpleMatrix> initDerivatives(Map<String, SimpleMatrix> map) {
      Map<String, SimpleMatrix> derivatives = Generics.newTreeMap();

      for (Map.Entry<String, SimpleMatrix> entry : map.entrySet()) {
        int numRows = entry.getValue().numRows();
        int numCols = entry.getValue().numCols();
        derivatives.put(entry.getKey(), new SimpleMatrix(numRows, numCols));
      }

      return derivatives;
    }
  }

  private ModelDerivatives scoreDerivatives(List<Tree> trainingBatch) {
    // "final" makes this as fast as having separate maps declared in this function
    final ModelDerivatives derivatives = new ModelDerivatives(model);

    List<Tree> forwardPropTrees = Generics.newArrayList();
    for (Tree tree : trainingBatch) {
      Tree trainingTree = tree.deepCopy();
      // this will attach the error vectors and the node vectors
      // to each node in the tree
      try {
        forwardPropagateTree(trainingTree);
      } catch(ForwardPropagationException e) {
        log.error("Illegal tree: " + trainingTree);
        throw e;
      }
      forwardPropTrees.add(trainingTree);
    }

    for (Tree tree : forwardPropTrees) {
      backpropDerivativesAndError(tree, derivatives.binaryTD, derivatives.binaryCD, derivatives.binaryTensorTD, derivatives.unaryCD, derivatives.wordVectorD);
      derivatives.error += sumError(tree);
    }

    return derivatives;
  }

  class ScoringProcessor implements ThreadsafeProcessor<List<Tree>, ModelDerivatives> {
    @Override
    public ModelDerivatives process(List<Tree> trainingBatch) {
      return scoreDerivatives(trainingBatch);
    }

    @Override
    public ThreadsafeProcessor<List<Tree>, ModelDerivatives> newInstance() {
      // should be threadsafe
      return this;
    }
  }

  @Override
  public void calculate(double[] theta) {
    model.vectorToParams(theta);

    final ModelDerivatives derivatives;
    if (model.op.trainOptions.nThreads == 1) {
      derivatives = scoreDerivatives(trainingBatch);
    } else {
      // TODO: because some addition operations happen in different
      // orders now, this results in slightly different values, which
      // over time add up to significantly different models even when
      // given the same random seed.  Probably not a big deal.
      // To be more specific, for trees T1, T2, T3, ... Tn,
      // when using one thread, we sum the derivatives T1 + T2 ...
      // When using multiple threads, we first sum T1 + ... + Tk,
      // then sum Tk+1 + ... + T2k, etc, for split size k.
      // The splits are then summed in order.
      // This different sum order results in slightly different numbers.
      MulticoreWrapper<List<Tree>, ModelDerivatives> wrapper =
        new MulticoreWrapper<>(model.op.trainOptions.nThreads, new ScoringProcessor());
      // use wrapper.nThreads in case the number of threads was automatically changed
      for (List<Tree> chunk : CollectionUtils.partitionIntoFolds(trainingBatch, wrapper.nThreads())) {
        wrapper.put(chunk);
      }
      wrapper.join();

      derivatives = new ModelDerivatives(model);
      while (wrapper.peek()) {
        ModelDerivatives batchDerivatives = wrapper.poll();
        derivatives.add(batchDerivatives);
      }
    }

    // scale the error by the number of sentences so that the
    // regularization isn't drowned out for large training batchs
    double scale = (1.0 / trainingBatch.size());
    value = derivatives.error * scale;

    value += scaleAndRegularize(derivatives.binaryTD, model.binaryTransform, scale, model.op.trainOptions.regTransformMatrix, false);
    value += scaleAndRegularize(derivatives.binaryCD, model.binaryClassification, scale, model.op.trainOptions.regClassification, true);
    value += scaleAndRegularizeTensor(derivatives.binaryTensorTD, model.binaryTensors, scale, model.op.trainOptions.regTransformTensor);
    value += scaleAndRegularize(derivatives.unaryCD, model.unaryClassification, scale, model.op.trainOptions.regClassification, false, true);
    value += scaleAndRegularize(derivatives.wordVectorD, model.wordVectors, scale, model.op.trainOptions.regWordVector, true, false);

    derivative = NeuralUtils.paramsToVector(theta.length, derivatives.binaryTD.valueIterator(), derivatives.binaryCD.valueIterator(), SimpleTensor.iteratorSimpleMatrix(derivatives.binaryTensorTD.valueIterator()), derivatives.unaryCD.values().iterator(), derivatives.wordVectorD.values().iterator());
  }

  private static double scaleAndRegularize(TwoDimensionalMap<String, String, SimpleMatrix> derivatives,
                                   TwoDimensionalMap<String, String, SimpleMatrix> currentMatrices,
                                   double scale, double regCost, boolean dropBiasColumn) {
    double cost = 0.0; // the regularization cost
    for (TwoDimensionalMap.Entry<String, String, SimpleMatrix> entry : currentMatrices) {
      SimpleMatrix D = derivatives.get(entry.getFirstKey(), entry.getSecondKey());
      SimpleMatrix regMatrix = entry.getValue();
      if (dropBiasColumn) {
        regMatrix = new SimpleMatrix(regMatrix);
        regMatrix.insertIntoThis(0, regMatrix.numCols() - 1, new SimpleMatrix(regMatrix.numRows(), 1));
      }
      D = D.scale(scale).plus(regMatrix.scale(regCost));
      derivatives.put(entry.getFirstKey(), entry.getSecondKey(), D);
      cost += regMatrix.elementMult(regMatrix).elementSum() * regCost / 2.0;
    }
    return cost;
  }

  private static double scaleAndRegularize(Map<String, SimpleMatrix> derivatives,
                                   Map<String, SimpleMatrix> currentMatrices,
                                   double scale, double regCost,
                                   boolean activeMatricesOnly, boolean dropBiasColumn) {
    double cost = 0.0; // the regularization cost
    for (Map.Entry<String, SimpleMatrix> entry : currentMatrices.entrySet()) {
      SimpleMatrix D = derivatives.get(entry.getKey());
      if (activeMatricesOnly && D == null) {
        // Fill in an emptpy matrix so the length of theta can match.
        // TODO: might want to allow for sparse parameter vectors
        derivatives.put(entry.getKey(), new SimpleMatrix(entry.getValue().numRows(), entry.getValue().numCols()));
        continue;
      }
      SimpleMatrix regMatrix = entry.getValue();
      if (dropBiasColumn) {
        regMatrix = new SimpleMatrix(regMatrix);
        regMatrix.insertIntoThis(0, regMatrix.numCols() - 1, new SimpleMatrix(regMatrix.numRows(), 1));
      }
      D = D.scale(scale).plus(regMatrix.scale(regCost));
      derivatives.put(entry.getKey(), D);
      cost += regMatrix.elementMult(regMatrix).elementSum() * regCost / 2.0;
    }
    return cost;
  }

  private static double scaleAndRegularizeTensor(TwoDimensionalMap<String, String, SimpleTensor> derivatives,
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
    if (goldClass >= 0) {
      goldLabel.set(goldClass, 1.0);
    }

    double nodeWeight = model.op.trainOptions.getClassWeight(goldClass);

    SimpleMatrix predictions = RNNCoreAnnotations.getPredictions(tree);

    // If this is an unlabeled class, set deltaClass to 0.  We could
    // make this more efficient by eliminating various of the below
    // calculations, but this would be the easiest way to handle the
    // unlabeled class
    SimpleMatrix deltaClass = goldClass >= 0 ? predictions.minus(goldLabel).scale(nodeWeight) : new SimpleMatrix(predictions.numRows(), predictions.numCols());
    SimpleMatrix localCD = deltaClass.mult(NeuralUtils.concatenateWithBias(currentVector).transpose());

    double error = -(NeuralUtils.elementwiseApplyLog(predictions).elementMult(goldLabel).elementSum());
    error = error * nodeWeight;
    RNNCoreAnnotations.setPredictionError(tree, error);

    if (tree.isPreTerminal()) { // below us is a word vector
      unaryCD.put(category, unaryCD.get(category).plus(localCD));

      String word = tree.children()[0].label().value();
      word = model.getVocabWord(word);

      //SimpleMatrix currentVectorDerivative = NeuralUtils.elementwiseApplyTanhDerivative(currentVector);
      //SimpleMatrix deltaFromClass = model.getUnaryClassification(category).transpose().mult(deltaClass);
      //SimpleMatrix deltaFull = deltaFromClass.extractMatrix(0, model.op.numHid, 0, 1).plus(deltaUp);
      //SimpleMatrix wordDerivative = deltaFull.elementMult(currentVectorDerivative);
      //wordVectorD.put(word, wordVectorD.get(word).plus(wordDerivative));

      SimpleMatrix currentVectorDerivative = NeuralUtils.elementwiseApplyTanhDerivative(currentVector);
      SimpleMatrix deltaFromClass = model.getUnaryClassification(category).transpose().mult(deltaClass);
      deltaFromClass = deltaFromClass.extractMatrix(0, model.op.numHid, 0, 1).elementMult(currentVectorDerivative);
      SimpleMatrix deltaFull = deltaFromClass.plus(deltaUp);
      SimpleMatrix oldWordVectorD = wordVectorD.get(word);
      if (oldWordVectorD == null) {
        wordVectorD.put(word, deltaFull);
      } else {
        wordVectorD.put(word, oldWordVectorD.plus(deltaFull));
      }
    } else {
      // Otherwise, this must be a binary node
      String leftCategory = model.basicCategory(tree.children()[0].label().value());
      String rightCategory = model.basicCategory(tree.children()[1].label().value());
      if (model.op.combineClassification) {
        unaryCD.put("", unaryCD.get("").plus(localCD));
      } else {
        binaryCD.put(leftCategory, rightCategory, binaryCD.get(leftCategory, rightCategory).plus(localCD));
      }

      SimpleMatrix currentVectorDerivative = NeuralUtils.elementwiseApplyTanhDerivative(currentVector);
      SimpleMatrix deltaFromClass = model.getBinaryClassification(leftCategory, rightCategory).transpose().mult(deltaClass);
      deltaFromClass = deltaFromClass.extractMatrix(0, model.op.numHid, 0, 1).elementMult(currentVectorDerivative);
      SimpleMatrix deltaFull = deltaFromClass.plus(deltaUp);

      SimpleMatrix leftVector = RNNCoreAnnotations.getNodeVector(tree.children()[0]);
      SimpleMatrix rightVector = RNNCoreAnnotations.getNodeVector(tree.children()[1]);
      SimpleMatrix childrenVector = NeuralUtils.concatenateWithBias(leftVector, rightVector);
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

      SimpleMatrix leftDerivative = NeuralUtils.elementwiseApplyTanhDerivative(leftVector);
      SimpleMatrix rightDerivative = NeuralUtils.elementwiseApplyTanhDerivative(rightVector);
      SimpleMatrix leftDeltaDown = deltaDown.extractMatrix(0, deltaFull.numRows(), 0, 1);
      SimpleMatrix rightDeltaDown = deltaDown.extractMatrix(deltaFull.numRows(), deltaFull.numRows() * 2, 0, 1);
      backpropDerivativesAndError(tree.children()[0], binaryTD, binaryCD, binaryTensorTD, unaryCD, wordVectorD, leftDerivative.elementMult(leftDeltaDown));
      backpropDerivativesAndError(tree.children()[1], binaryTD, binaryCD, binaryTensorTD, unaryCD, wordVectorD, rightDerivative.elementMult(rightDeltaDown));
    }
  }

  private static SimpleMatrix computeTensorDeltaDown(SimpleMatrix deltaFull, SimpleMatrix leftVector, SimpleMatrix rightVector,
                                              SimpleMatrix W, SimpleTensor Wt) {
    SimpleMatrix WTDelta = W.transpose().mult(deltaFull);
    SimpleMatrix WTDeltaNoBias = WTDelta.extractMatrix(0, deltaFull.numRows() * 2, 0, 1);
    int size = deltaFull.getNumElements();
    SimpleMatrix deltaTensor = new SimpleMatrix(size*2, 1);
    SimpleMatrix fullVector = NeuralUtils.concatenate(leftVector, rightVector);
    for (int slice = 0; slice < size; ++slice) {
      SimpleMatrix scaledFullVector = fullVector.scale(deltaFull.get(slice));
      deltaTensor = deltaTensor.plus(Wt.getSlice(slice).plus(Wt.getSlice(slice).transpose()).mult(scaledFullVector));
    }
    return deltaTensor.plus(WTDeltaNoBias);
  }

  private static SimpleTensor getTensorGradient(SimpleMatrix deltaFull, SimpleMatrix leftVector, SimpleMatrix rightVector) {
    int size = deltaFull.getNumElements();
    SimpleTensor Wt_df = new SimpleTensor(size*2, size*2, size);
    // TODO: combine this concatenation with computeTensorDeltaDown?
    SimpleMatrix fullVector = NeuralUtils.concatenate(leftVector, rightVector);
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
    SimpleMatrix nodeVector; // initialized below or Exception thrown // = null;
    SimpleMatrix classification; // initialized below or Exception thrown // = null;

    if (tree.isLeaf()) {
      // We do nothing for the leaves.  The preterminals will
      // calculate the classification for this word/tag.  In fact, the
      // recursion should not have gotten here (unless there are
      // degenerate trees of just one leaf)
      throw new ForwardPropagationException("We should not have reached leaves in forwardPropagate");
    } else if (tree.isPreTerminal()) {
      classification = model.getUnaryClassification(tree.label().value());
      String word = tree.children()[0].label().value();
      SimpleMatrix wordVector = model.getWordVector(word);
      nodeVector = NeuralUtils.elementwiseApplyTanh(wordVector);
    } else if (tree.children().length == 1) {
      throw new ForwardPropagationException("Non-preterminal nodes of size 1 should have already been collapsed");
    } else if (tree.children().length == 2) {
      forwardPropagateTree(tree.children()[0]);
      forwardPropagateTree(tree.children()[1]);

      String leftCategory = tree.children()[0].label().value();
      String rightCategory = tree.children()[1].label().value();
      SimpleMatrix W = model.getBinaryTransform(leftCategory, rightCategory);
      classification = model.getBinaryClassification(leftCategory, rightCategory);

      SimpleMatrix leftVector = RNNCoreAnnotations.getNodeVector(tree.children()[0]);
      SimpleMatrix rightVector = RNNCoreAnnotations.getNodeVector(tree.children()[1]);
      SimpleMatrix childrenVector = NeuralUtils.concatenateWithBias(leftVector, rightVector);
      if (model.op.useTensors) {
        SimpleTensor tensor = model.getBinaryTensor(leftCategory, rightCategory);
        SimpleMatrix tensorIn = NeuralUtils.concatenate(leftVector, rightVector);
        SimpleMatrix tensorOut = tensor.bilinearProducts(tensorIn);
        nodeVector = NeuralUtils.elementwiseApplyTanh(W.mult(childrenVector).plus(tensorOut));
      } else {
        nodeVector = NeuralUtils.elementwiseApplyTanh(W.mult(childrenVector));
      }
    } else {
      StringBuilder error = new StringBuilder();
      error.append("SentimentCostAndGradient: Tree not correctly binarized:\n   ");
      error.append(tree);
      error.append("\nToo many top level constituents present: ");
      error.append("(" + tree.value());
      for (Tree child : tree.children()) {
        error.append(" (" + child.value() + " ...)");
      }
      error.append(")");
      throw new ForwardPropagationException(error.toString());
    }

    SimpleMatrix predictions = NeuralUtils.softmax(classification.mult(NeuralUtils.concatenateWithBias(nodeVector)));

    int index = getPredictedClass(predictions);
    if (!(tree.label() instanceof CoreLabel)) {
      log.info("SentimentCostAndGradient: warning: No CoreLabels in nodes: " + tree);
      throw new AssertionError("Expected CoreLabels in the nodes");
    }
    CoreLabel label = (CoreLabel) tree.label();
    label.set(RNNCoreAnnotations.Predictions.class, predictions);
    label.set(RNNCoreAnnotations.PredictedClass.class, index);
    label.set(RNNCoreAnnotations.NodeVector.class, nodeVector);
  } // end forwardPropagateTree

}
