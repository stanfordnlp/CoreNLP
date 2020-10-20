package edu.stanford.nlp.parser.dvparser; 
import edu.stanford.nlp.util.logging.Redwood;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.neural.NeuralUtils;
import edu.stanford.nlp.optimization.AbstractCachingDiffFunction;
import edu.stanford.nlp.parser.common.NoSuchParseException;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.parser.metrics.TreeSpanScoring;
import edu.stanford.nlp.trees.DeepTree;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.IntPair;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.util.TwoDimensionalMap;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;

public class DVParserCostAndGradient extends AbstractCachingDiffFunction  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(DVParserCostAndGradient.class);
  List<Tree> trainingBatch;
  IdentityHashMap<Tree, List<Tree>> topParses;
  DVModel dvModel;
  Options op;

  public DVParserCostAndGradient(List<Tree> trainingBatch,
                                 IdentityHashMap<Tree, List<Tree>> topParses,
                                 DVModel dvModel, Options op) {
    this.trainingBatch = trainingBatch;
    this.topParses = topParses;
    this.dvModel = dvModel;
    this.op = op;
  }

  /**
   * Return a null list if we don't care about context words, return a
   * list of the words at the leaves of the tree if we do care
   */
  private List<String> getContextWords(Tree tree) {
    List<String> words = null;
    if (op.trainOptions.useContextWords) {
      words = Generics.newArrayList();
      List<Label> leaves = tree.yield();
      for (Label word : leaves) {
        words.add(word.value());
      }
    }
    return words;
  }

  private SimpleMatrix concatenateContextWords(SimpleMatrix childVec, IntPair span, List<String> words) {
    // TODO: factor out getting the words
    SimpleMatrix left = (span.getSource() < 0) ? dvModel.getStartWordVector() : dvModel.getWordVector(words.get(span.getSource()));
    SimpleMatrix right = (span.getTarget() >= words.size()) ? dvModel.getEndWordVector() : dvModel.getWordVector(words.get(span.getTarget()));
    return NeuralUtils.concatenate(childVec, left, right);
  }

  public static void outputSpans(Tree tree) {
    log.info(tree.getSpan() + " ");
    for (Tree child : tree.children()) {
      outputSpans(child);
    }
  }

  // TODO: make this part of DVModel or DVParser?
  public double score(Tree tree, IdentityHashMap<Tree, SimpleMatrix> nodeVectors) {
    List<String> words = getContextWords(tree);
    // score of the entire tree is the sum of the scores of all of
    // its nodes
    // TODO: make the node vectors part of the tree itself?
    IdentityHashMap<Tree, Double> scores = new IdentityHashMap<>();
    try {
      forwardPropagateTree(tree, words, nodeVectors, scores);
    } catch (AssertionError e) {
      log.info("Failed to correctly process tree " + tree);
      throw e;
    }

    BigDecimal score = new BigDecimal(0);
    for (Double value : scores.values()) {
      score = score.add(new BigDecimal(value));
      //log.info(score.toString());
    }
    return score.doubleValue();
  }

  private void forwardPropagateTree(Tree tree, List<String> words,
                                    IdentityHashMap<Tree, SimpleMatrix> nodeVectors,
                                    IdentityHashMap<Tree, Double> scores) {
    if (tree.isLeaf()) {
      return;
    }

    if (tree.isPreTerminal()) {
      Tree wordNode = tree.children()[0];
      String word = wordNode.label().value();
      SimpleMatrix wordVector = dvModel.getWordVector(word);
      wordVector = NeuralUtils.elementwiseApplyTanh(wordVector);
      nodeVectors.put(tree, wordVector);
      return;
    }

    for (Tree child : tree.children()) {
      forwardPropagateTree(child, words, nodeVectors, scores);
    }

    // at this point, nodeVectors contains the vectors for all of
    // the children of tree

    SimpleMatrix childVec;
    if (tree.children().length == 2) {
      childVec = NeuralUtils.concatenateWithBias(nodeVectors.get(tree.children()[0]), nodeVectors.get(tree.children()[1]));
    } else {
      childVec = NeuralUtils.concatenateWithBias(nodeVectors.get(tree.children()[0]));
    }
    if (op.trainOptions.useContextWords) {
      childVec = concatenateContextWords(childVec, tree.getSpan(), words);
    }

    SimpleMatrix W = dvModel.getWForNode(tree);
    if (W == null) {
      String error = "Could not find W for tree " + tree;
      if (op.testOptions.verbose) {
        log.info(error);
      }
      throw new NoSuchParseException(error);
    }
    SimpleMatrix currentVector = W.mult(childVec);
    currentVector = NeuralUtils.elementwiseApplyTanh(currentVector);
    nodeVectors.put(tree, currentVector);

    SimpleMatrix scoreW = dvModel.getScoreWForNode(tree);
    if (scoreW == null) {
      String error = "Could not find scoreW for tree " + tree;
      if (op.testOptions.verbose) {
        log.info(error);
      }
      throw new NoSuchParseException(error);
    }
    double score = scoreW.dot(currentVector);
    //score = NeuralUtils.sigmoid(score);
    scores.put(tree, score);
    //log.info(Double.toString(score)+" ");
  }

  public int domainDimension() {
    // TODO: cache this for speed?
    return dvModel.totalParamSize();
  }

  static final double TRAIN_LAMBDA = 1.0;

  public List<DeepTree> getAllHighestScoringTreesTest(List<Tree> trees){
	  List<DeepTree> allBestTrees = new ArrayList<>();
	  for (Tree tree : trees) {
		  allBestTrees.add(getHighestScoringTree(tree, 0));
	  }
	  return allBestTrees;
  }

  public DeepTree getHighestScoringTree(Tree tree, double lambda){
    List<Tree> hypotheses = topParses.get(tree);
    if (hypotheses == null || hypotheses.size() == 0) {
      throw new AssertionError("Failed to get any hypothesis trees for " + tree);
    }
    double bestScore = Double.NEGATIVE_INFINITY;
    Tree bestTree = null;
    IdentityHashMap<Tree, SimpleMatrix> bestVectors = null;
    for (Tree hypothesis : hypotheses) {
      IdentityHashMap<Tree, SimpleMatrix> nodeVectors = new IdentityHashMap<>();
      double scoreHyp = score(hypothesis, nodeVectors);
      double deltaMargin =0;
      if (lambda != 0){
        //TODO: RS: Play around with this parameter to prevent blowing up of scores
        deltaMargin = op.trainOptions.deltaMargin * lambda * getMargin(tree, hypothesis);
      }

      scoreHyp = scoreHyp + deltaMargin;
      if (bestTree == null || scoreHyp > bestScore) {
        bestTree = hypothesis;
        bestScore = scoreHyp;
        bestVectors = nodeVectors;
      }
    }

    DeepTree returnTree = new DeepTree(bestTree,bestVectors,bestScore);
    return returnTree;
  }

  class ScoringProcessor implements ThreadsafeProcessor<Tree, Pair<DeepTree, DeepTree>> {
    @Override
    public Pair<DeepTree, DeepTree> process(Tree tree) {
      // For each tree, move in the direction of the gold tree, and
      // move away from the direction of the best scoring hypothesis

      IdentityHashMap<Tree, SimpleMatrix> goldVectors = new IdentityHashMap<>();
      double scoreGold = score(tree, goldVectors);
      DeepTree bestTree = getHighestScoringTree(tree, TRAIN_LAMBDA);
      DeepTree goldTree = new DeepTree(tree, goldVectors, scoreGold);
      return Pair.makePair(goldTree, bestTree);
    }

    @Override
    public ThreadsafeProcessor<Tree, Pair<DeepTree, DeepTree>> newInstance() {
      // should be threadsafe
      return this;
    }
  }


  // fill value & derivative
  public void calculate(double[] theta) {
    dvModel.vectorToParams(theta);

    double localValue = 0.0;
    double[] localDerivative = new double[theta.length];

    TwoDimensionalMap<String, String, SimpleMatrix> binaryW_dfsG,binaryW_dfsB;
    binaryW_dfsG = TwoDimensionalMap.treeMap();
    binaryW_dfsB = TwoDimensionalMap.treeMap();
    TwoDimensionalMap<String, String, SimpleMatrix> binaryScoreDerivativesG,binaryScoreDerivativesB ;
    binaryScoreDerivativesG = TwoDimensionalMap.treeMap();
    binaryScoreDerivativesB = TwoDimensionalMap.treeMap();
    Map<String, SimpleMatrix> unaryW_dfsG,unaryW_dfsB ;
    unaryW_dfsG = new TreeMap<>();
    unaryW_dfsB = new TreeMap<>();
    Map<String, SimpleMatrix> unaryScoreDerivativesG,unaryScoreDerivativesB ;
    unaryScoreDerivativesG = new TreeMap<>();
    unaryScoreDerivativesB= new TreeMap<>();

    Map<String, SimpleMatrix> wordVectorDerivativesG = new TreeMap<>();
    Map<String, SimpleMatrix> wordVectorDerivativesB = new TreeMap<>();

    for (TwoDimensionalMap.Entry<String, String, SimpleMatrix> entry : dvModel.binaryTransform) {
      int numRows = entry.getValue().numRows();
      int numCols = entry.getValue().numCols();
      binaryW_dfsG.put(entry.getFirstKey(), entry.getSecondKey(), new SimpleMatrix(numRows, numCols));
      binaryW_dfsB.put(entry.getFirstKey(), entry.getSecondKey(), new SimpleMatrix(numRows, numCols));
      binaryScoreDerivativesG.put(entry.getFirstKey(), entry.getSecondKey(), new SimpleMatrix(1, numRows));
      binaryScoreDerivativesB.put(entry.getFirstKey(), entry.getSecondKey(), new SimpleMatrix(1, numRows));
    }
    for (Map.Entry<String, SimpleMatrix> entry : dvModel.unaryTransform.entrySet()) {
      int numRows = entry.getValue().numRows();
      int numCols = entry.getValue().numCols();
      unaryW_dfsG.put(entry.getKey(), new SimpleMatrix(numRows, numCols));
      unaryW_dfsB.put(entry.getKey(), new SimpleMatrix(numRows, numCols));
      unaryScoreDerivativesG.put(entry.getKey(), new SimpleMatrix(1, numRows));
      unaryScoreDerivativesB.put(entry.getKey(), new SimpleMatrix(1, numRows));
    }
    if (op.trainOptions.trainWordVectors) {
      for (Map.Entry<String, SimpleMatrix> entry : dvModel.wordVectors.entrySet()) {
        int numRows = entry.getValue().numRows();
        int numCols = entry.getValue().numCols();
        wordVectorDerivativesG.put(entry.getKey(), new SimpleMatrix(numRows, numCols));
        wordVectorDerivativesB.put(entry.getKey(), new SimpleMatrix(numRows, numCols));
      }
    }

    // Some optimization methods prints out a line without an end, so our
    // debugging statements are misaligned
    Timing scoreTiming = new Timing();
    scoreTiming.doing("Scoring trees");
    int treeNum = 0;
    MulticoreWrapper<Tree, Pair<DeepTree, DeepTree>> wrapper = new MulticoreWrapper<>(op.trainOptions.trainingThreads, new ScoringProcessor());
    for (Tree tree : trainingBatch) {
      wrapper.put(tree);
    }
    wrapper.join();
    scoreTiming.done();
    while (wrapper.peek()) {
      Pair<DeepTree, DeepTree> result = wrapper.poll();
      DeepTree goldTree = result.first;
      DeepTree bestTree = result.second;

      StringBuilder treeDebugLine = new StringBuilder();
      Formatter formatter = new Formatter(treeDebugLine);
      boolean isDone = (Math.abs(bestTree.getScore() - goldTree.getScore()) <= 0.00001 || goldTree.getScore() > bestTree.getScore());
      String done = isDone ? "done" : "";
      formatter.format("Tree %6d Highest tree: %12.4f Correct tree: %12.4f %s", treeNum, bestTree.getScore(), goldTree.getScore(), done);
      log.info(treeDebugLine.toString());
      if (!isDone){
        // if the gold tree is better than the best hypothesis tree by
        // a large enough margin, then the score difference will be 0
        // and we ignore the tree

        double valueDelta = bestTree.getScore() - goldTree.getScore();
        //double valueDelta = Math.max(0.0, - scoreGold + bestScore);
        localValue += valueDelta;

        // get the context words for this tree - should be the same
        // for either goldTree or bestTree
        List<String> words = getContextWords(goldTree.getTree());

        // The derivatives affected by this tree are only based on the
        // nodes present in this tree, eg not all matrix derivatives
        // will be affected by this tree
        backpropDerivative(goldTree.getTree(), words, goldTree.getVectors(),
                           binaryW_dfsG, unaryW_dfsG,
                           binaryScoreDerivativesG, unaryScoreDerivativesG,
                           wordVectorDerivativesG);

        backpropDerivative(bestTree.getTree(), words, bestTree.getVectors(),
                           binaryW_dfsB, unaryW_dfsB,
                           binaryScoreDerivativesB, unaryScoreDerivativesB,
                           wordVectorDerivativesB);

      }
      ++treeNum;
    }

    double[] localDerivativeGood;
    double[] localDerivativeB;
    if (op.trainOptions.trainWordVectors) {
      localDerivativeGood = NeuralUtils.paramsToVector(theta.length,
                                                       binaryW_dfsG.valueIterator(), unaryW_dfsG.values().iterator(),
                                                       binaryScoreDerivativesG.valueIterator(),
                                                       unaryScoreDerivativesG.values().iterator(),
                                                       wordVectorDerivativesG.values().iterator());

      localDerivativeB = NeuralUtils.paramsToVector(theta.length,
                                                    binaryW_dfsB.valueIterator(), unaryW_dfsB.values().iterator(),
                                                    binaryScoreDerivativesB.valueIterator(),
                                                    unaryScoreDerivativesB.values().iterator(),
                                                    wordVectorDerivativesB.values().iterator());
    } else {
      localDerivativeGood = NeuralUtils.paramsToVector(theta.length,
                                                       binaryW_dfsG.valueIterator(), unaryW_dfsG.values().iterator(),
                                                       binaryScoreDerivativesG.valueIterator(),
                                                       unaryScoreDerivativesG.values().iterator());

      localDerivativeB = NeuralUtils.paramsToVector(theta.length,
                                                    binaryW_dfsB.valueIterator(), unaryW_dfsB.values().iterator(),
                                                    binaryScoreDerivativesB.valueIterator(),
                                                    unaryScoreDerivativesB.values().iterator());
    }

    // correct - highest
    for (int i =0 ;i<localDerivativeGood.length;i++){
      localDerivative[i] = localDerivativeB[i] - localDerivativeGood[i];
    }

    // TODO: this is where we would combine multiple costs if we had parallelized the calculation
    value = localValue;
    derivative = localDerivative;

    // normalizing by training batch size
    value = (1.0/trainingBatch.size()) * value;
    ArrayMath.multiplyInPlace(derivative, (1.0/trainingBatch.size()));

    // add regularization to cost:
    double[] currentParams = dvModel.paramsToVector();
    double regCost = 0;
    for (double currentParam : currentParams) {
      regCost += currentParam * currentParam;
    }
    regCost = op.trainOptions.regCost * 0.5 * regCost;
    value  += regCost;
    // add regularization to gradient
    ArrayMath.multiplyInPlace(currentParams, op.trainOptions.regCost);
    ArrayMath.pairwiseAddInPlace(derivative, currentParams);

  }

  public double getMargin(Tree goldTree, Tree bestHypothesis) {
    return TreeSpanScoring.countSpanErrors(op.langpack(), goldTree, bestHypothesis);
  }


  public void backpropDerivative(Tree tree, List<String> words,
                                 IdentityHashMap<Tree, SimpleMatrix> nodeVectors,
                                 TwoDimensionalMap<String, String, SimpleMatrix> binaryW_dfs,
                                 Map<String, SimpleMatrix> unaryW_dfs,
                                 TwoDimensionalMap<String, String, SimpleMatrix> binaryScoreDerivatives,
                                 Map<String, SimpleMatrix> unaryScoreDerivatives,
                                 Map<String, SimpleMatrix> wordVectorDerivatives) {
    SimpleMatrix delta = new SimpleMatrix(op.lexOptions.numHid, 1);
    backpropDerivative(tree, words, nodeVectors,
                       binaryW_dfs, unaryW_dfs,
                       binaryScoreDerivatives, unaryScoreDerivatives, wordVectorDerivatives,
                       delta);
  }

  public void backpropDerivative(Tree tree, List<String> words,
                                 IdentityHashMap<Tree, SimpleMatrix> nodeVectors,
                                 TwoDimensionalMap<String, String, SimpleMatrix> binaryW_dfs,
                                 Map<String, SimpleMatrix> unaryW_dfs,
                                 TwoDimensionalMap<String, String, SimpleMatrix> binaryScoreDerivatives,
                                 Map<String, SimpleMatrix> unaryScoreDerivatives,
                                 Map<String, SimpleMatrix> wordVectorDerivatives,
                                 SimpleMatrix deltaUp) {
    if (tree.isLeaf()) {
      return;
    }
    if (tree.isPreTerminal()) {
      if (op.trainOptions.trainWordVectors) {
        String word = tree.children()[0].label().value();
        word = dvModel.getVocabWord(word);
//        SimpleMatrix currentVector = nodeVectors.get(tree);
//        SimpleMatrix currentVectorDerivative = nonlinearityVectorToDerivative(currentVector);
//        SimpleMatrix derivative = deltaUp.elementMult(currentVectorDerivative);
        SimpleMatrix derivative = deltaUp;
        wordVectorDerivatives.put(word, wordVectorDerivatives.get(word).plus(derivative));
      }
      return;
    }
    SimpleMatrix currentVector = nodeVectors.get(tree);
    SimpleMatrix currentVectorDerivative = NeuralUtils.elementwiseApplyTanhDerivative(currentVector);

    SimpleMatrix scoreW = dvModel.getScoreWForNode(tree);
    currentVectorDerivative = currentVectorDerivative.elementMult(scoreW.transpose());

    // the delta that is used at the current nodes
    SimpleMatrix deltaCurrent = deltaUp.plus(currentVectorDerivative);
    SimpleMatrix W = dvModel.getWForNode(tree);
    SimpleMatrix WTdelta = W.transpose().mult(deltaCurrent);

    if (tree.children().length == 2) {
      //TODO: RS: Change to the nice "getWForNode" setup?
      String leftLabel = dvModel.basicCategory(tree.children()[0].label().value());
      String rightLabel = dvModel.basicCategory(tree.children()[1].label().value());

      binaryScoreDerivatives.put(leftLabel, rightLabel,
                                 binaryScoreDerivatives.get(leftLabel, rightLabel).plus(currentVector.transpose()));


      SimpleMatrix leftVector = nodeVectors.get(tree.children()[0]);
      SimpleMatrix rightVector = nodeVectors.get(tree.children()[1]);
      SimpleMatrix childrenVector = NeuralUtils.concatenateWithBias(leftVector, rightVector);
      if (op.trainOptions.useContextWords) {
        childrenVector = concatenateContextWords(childrenVector, tree.getSpan(), words);
      }
      SimpleMatrix W_df = deltaCurrent.mult(childrenVector.transpose());
      binaryW_dfs.put(leftLabel, rightLabel, binaryW_dfs.get(leftLabel, rightLabel).plus(W_df));

      // and then recurse
      SimpleMatrix leftDerivative = NeuralUtils.elementwiseApplyTanhDerivative(leftVector);
      SimpleMatrix rightDerivative = NeuralUtils.elementwiseApplyTanhDerivative(rightVector);
      SimpleMatrix leftWTDelta = WTdelta.extractMatrix(0, deltaCurrent.numRows(), 0, 1);
      SimpleMatrix rightWTDelta = WTdelta.extractMatrix(deltaCurrent.numRows(), deltaCurrent.numRows() * 2, 0, 1);
      backpropDerivative(tree.children()[0], words, nodeVectors,
                         binaryW_dfs, unaryW_dfs,
                         binaryScoreDerivatives, unaryScoreDerivatives, wordVectorDerivatives,
                         leftDerivative.elementMult(leftWTDelta));
      backpropDerivative(tree.children()[1], words, nodeVectors,
                         binaryW_dfs, unaryW_dfs,
                         binaryScoreDerivatives, unaryScoreDerivatives, wordVectorDerivatives,
                         rightDerivative.elementMult(rightWTDelta));
    } else if (tree.children().length == 1) {
      String childLabel = dvModel.basicCategory(tree.children()[0].label().value());

      unaryScoreDerivatives.put(childLabel,unaryScoreDerivatives.get(childLabel).plus(currentVector.transpose()));

      SimpleMatrix childVector = nodeVectors.get(tree.children()[0]);
      SimpleMatrix childVectorWithBias = NeuralUtils.concatenateWithBias(childVector);
      if (op.trainOptions.useContextWords) {
        childVectorWithBias = concatenateContextWords(childVectorWithBias, tree.getSpan(), words);
      }
      SimpleMatrix W_df = deltaCurrent.mult(childVectorWithBias.transpose());

      // System.out.println("unary backprop derivative for " + childLabel);
      // System.out.println("Old transform:");
      // System.out.println(unaryW_dfs.get(childLabel));
      // System.out.println(" Delta:");
      // System.out.println(W_df.scale(scale));
      unaryW_dfs.put(childLabel,unaryW_dfs.get(childLabel).plus(W_df));

      // and then recurse
      SimpleMatrix childDerivative = NeuralUtils.elementwiseApplyTanhDerivative(childVector);
      //SimpleMatrix childDerivative = childVector;
      SimpleMatrix childWTDelta = WTdelta.extractMatrix(0, deltaCurrent.numRows(), 0, 1);
      backpropDerivative(tree.children()[0], words, nodeVectors,
                         binaryW_dfs, unaryW_dfs,
                         binaryScoreDerivatives, unaryScoreDerivatives, wordVectorDerivatives,
                         childDerivative.elementMult(childWTDelta));
    }
  }
}

