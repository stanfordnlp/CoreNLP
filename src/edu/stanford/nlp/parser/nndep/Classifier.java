package edu.stanford.nlp.parser.nndep;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.LeastRecentlyUsedCache;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;
import edu.stanford.nlp.util.logging.Redwood;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

/**
 * Neural network classifier which powers a transition-based dependency parser.
 *
 * This classifier is built to accept distributed-representation
 * inputs, and feeds back errors to these input layers as it learns.
 *
 * <p>
 * In order to train a classifier, instantiate this class using the
 * {@link #Classifier(Config, Dataset, double[][], double[][], double[], double[][], java.util.List)}
 * constructor. (The presence of a non-null dataset signals that we
 * wish to train.) After training by alternating calls to
 * {@link #computeCostFunction(int, double, double)} and,
 * {@link #takeAdaGradientStep(edu.stanford.nlp.parser.nndep.Classifier.Cost, double, double)},
 * be sure to call {@link #finalizeTraining()} in order to allow the
 * classifier to clean up resources used during training.
 *
 * @author Danqi Chen
 * @author Jon Gauthier
 */
public class Classifier  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(Classifier.class);
  // E: numFeatures x embeddingSize
  // W1: hiddenSize x (embeddingSize x numFeatures)
  // b1: hiddenSize
  // W2: numLabels x hiddenSize

  // Weight matrices
  private final float[][] W1, W2, E;
  private final float[] b1;

  // Global gradSaved
  private double[][] gradSaved;

  // Gradient histories
  private double[][] eg2W1, eg2W2, eg2E;
  private double[] eg2b1;

  /**
   * Pre-computed hidden layer unit activations. Each double array
   * within this data is an entire hidden layer. The sub-arrays are
   * indexed somewhat arbitrarily; in order to find hidden-layer unit
   * activations for a given feature ID, use {@link #preMap} to find
   * the proper index into this data.
   */
  private double[][] saved;

  /**
   * Describes features which should be precomputed. Each entry maps a
   * feature ID to its destined index in the saved hidden unit
   * activation data (see {@link #saved}).
   */
  private final Map<Integer, Integer> preMap;

  /**
   * Initial training state is dependent on how the classifier is
   * initialized. We use this flag to determine whether calls to
   * {@link #computeCostFunction(int, double, double)}, etc. are valid.
   */
  private boolean isTraining;

  /**
   * All training examples.
   */
  private final Dataset dataset;

  /**
   * We use MulticoreWrapper to parallelize mini-batch training.
   * <p>
   * Threaded job input: partition of minibatch;
   * current weights + params
   * Threaded job output: cost value, weight gradients for partition of
   * minibatch
   */
  private final MulticoreWrapper<Pair<Collection<Example>, FeedforwardParams>, Cost> jobHandler;

  private final Config config;

  /**
   * Number of possible dependency relation labels among which this
   * classifier will choose.
   */
  private final int numLabels;

  private LeastRecentlyUsedCache<Integer, float[]> cache;

  /**
   * Instantiate a classifier with previously learned parameters in
   * order to perform new inference.
   *
   * @param config
   * @param E
   * @param W1
   * @param b1
   * @param W2
   * @param preComputed
   */
  public Classifier(Config config, float[][] E, float[][] W1, float[] b1, float[][] W2, List<Integer> preComputed) {
    this(config, null, E, W1, b1, W2, preComputed);
  }

  /**
   * Instantiate a classifier with training data and randomly
   * initialized parameter matrices in order to begin training.
   *
   * @param config
   * @param dataset
   * @param E
   * @param W1
   * @param b1
   * @param W2
   * @param preComputed
   */
  public Classifier(Config config, Dataset dataset, float[][] E, float[][] W1, float[] b1, float[][] W2,
                    List<Integer> preComputed) {
    this.config = config;
    this.dataset = dataset;

    this.E = E;
    this.W1 = W1;
    this.b1 = b1;
    this.W2 = W2;

    initGradientHistories();

    numLabels = W2.length;

    preMap = new HashMap<>();
    for (int i = 0; i < preComputed.size() && i < config.numPreComputed; ++i)
      preMap.put(preComputed.get(i), i);

    isTraining = dataset != null;
    if (isTraining)
      jobHandler = new MulticoreWrapper<>(config.trainingThreads, new CostFunction(), false);
    else
      jobHandler = null;

    cache = new LeastRecentlyUsedCache<>(config.numCached);
  }

  /**
   * Evaluates the training cost of a particular subset of training
   * examples given the current learned weights.
   *
   * This function will be evaluated in parallel on different data in
   * separate threads, and accesses the classifier's weights stored in
   * the outer class instance.
   *
   * Each nested class instance accumulates its own weight gradients;
   * these gradients will be merged on a main thread after all cost
   * function runs complete.
   *
   * @see #computeCostFunction(int, double, double)
   */
  private class CostFunction implements ThreadsafeProcessor<Pair<Collection<Example>, FeedforwardParams>, Cost> {

    private double[][] gradW1;
    private double[] gradb1;
    private double[][] gradW2;
    private double[][] gradE;

    @Override
    public Cost process(Pair<Collection<Example>, FeedforwardParams> input) {
      Collection<Example> examples = input.first();
      FeedforwardParams params = input.second();

      // We can't fix the seed used with ThreadLocalRandom
      // TODO: Is this a serious problem?
      ThreadLocalRandom random = ThreadLocalRandom.current();

      gradW1 = new double[W1.length][W1[0].length];
      gradb1 = new double[b1.length];
      gradW2 = new double[W2.length][W2[0].length];
      gradE = new double[E.length][E[0].length];

      double cost = 0.0;
      double correct = 0.0;

      for (Example ex : examples) {
        List<Integer> feature = ex.getFeature();
        List<Integer> label = ex.getLabel();

        double[] scores = new double[numLabels];
        double[] hidden = new double[config.hiddenSize];
        double[] hidden3 = new double[config.hiddenSize];

        // Run dropout: randomly drop some hidden-layer units. `ls`
        // contains the indices of those units which are still active
        int[] ls = IntStream.range(0, config.hiddenSize)
                            .filter(n -> random.nextDouble() > params.getDropOutProb())
                            .toArray();

        int offset = 0;
        for (int j = 0; j < config.numTokens; ++j) {
          int tok = feature.get(j);
          int index = tok * config.numTokens + j;

          if (preMap.containsKey(index)) {
            // Unit activations for this input feature value have been
            // precomputed
            int id = preMap.get(index);

            // Only extract activations for those nodes which are still
            // activated (`ls`)
            for (int nodeIndex : ls)
              hidden[nodeIndex] += saved[id][nodeIndex];
          } else {
            for (int nodeIndex : ls) {
              for (int k = 0; k < config.embeddingSize; ++k)
                hidden[nodeIndex] += W1[nodeIndex][offset + k] * E[tok][k];
            }
          }
          offset += config.embeddingSize;
        }

        // Add bias term and apply activation function
        for (int nodeIndex : ls) {
          hidden[nodeIndex] += b1[nodeIndex];
          hidden3[nodeIndex] = Math.pow(hidden[nodeIndex], 3);
        }

        // Feed forward to softmax layer (no activation yet)
        int optLabel = -1;
        for (int i = 0; i < numLabels; ++i) {
          if (label.get(i) >= 0) {
            for (int nodeIndex : ls)
              scores[i] += W2[i][nodeIndex] * hidden3[nodeIndex];

            if (optLabel < 0 || scores[i] > scores[optLabel])
              optLabel = i;
          }
        }

        double sum1 = 0.0;
        double sum2 = 0.0;
        double maxScore = scores[optLabel];
        for (int i = 0; i < numLabels; ++i) {
          if (label.get(i) >= 0) {
            scores[i] = Math.exp(scores[i] - maxScore);
            if (label.get(i) == 1) sum1 += scores[i];
            sum2 += scores[i];
          }
        }

        cost += (Math.log(sum2) - Math.log(sum1)) / params.getBatchSize();
        if (label.get(optLabel) == 1)
          correct += +1.0 / params.getBatchSize();

        double[] gradHidden3 = new double[config.hiddenSize];
        for (int i = 0; i < numLabels; ++i)
          if (label.get(i) >= 0) {
            double delta = -(label.get(i) - scores[i] / sum2) / params.getBatchSize();
            for (int nodeIndex : ls) {
              gradW2[i][nodeIndex] += delta * hidden3[nodeIndex];
              gradHidden3[nodeIndex] += delta * W2[i][nodeIndex];
            }
          }

        double[] gradHidden = new double[config.hiddenSize];
        for (int nodeIndex : ls) {
          gradHidden[nodeIndex] = gradHidden3[nodeIndex] * 3 * hidden[nodeIndex] * hidden[nodeIndex];
          gradb1[nodeIndex] += gradHidden[nodeIndex];
        }

        offset = 0;
        for (int j = 0; j < config.numTokens; ++j) {
          int tok = feature.get(j);
          int index = tok * config.numTokens + j;
          if (preMap.containsKey(index)) {
            int id = preMap.get(index);
            for (int nodeIndex : ls)
              gradSaved[id][nodeIndex] += gradHidden[nodeIndex];
          } else {
            for (int nodeIndex : ls) {
              for (int k = 0; k < config.embeddingSize; ++k) {
                gradW1[nodeIndex][offset + k] += gradHidden[nodeIndex] * E[tok][k];
                gradE[tok][k] += gradHidden[nodeIndex] * W1[nodeIndex][offset + k];
              }
            }
          }
          offset += config.embeddingSize;
        }
      }

      return new Cost(cost, correct, gradW1, gradb1, gradW2, gradE);
    }

    /**
     * Return a new threadsafe instance.
     */
    @Override
    public ThreadsafeProcessor<Pair<Collection<Example>, FeedforwardParams>, Cost> newInstance() {
      return new CostFunction();
    }
  }

  /**
   * Describes the parameters for a particular invocation of a cost
   * function.
   */
  private static class FeedforwardParams {

    /**
     * Size of the entire mini-batch (not just the chunk that might be
     * fed-forward at this moment).
     */
    private final int batchSize;

    private final double dropOutProb;

    private FeedforwardParams(int batchSize, double dropOutProb) {
      this.batchSize = batchSize;
      this.dropOutProb = dropOutProb;
    }

    public int getBatchSize() {
      return batchSize;
    }

    public double getDropOutProb() {
      return dropOutProb;
    }

  }

  /**
   * Describes the result of feedforward + backpropagation through
   * the neural network for the batch provided to a `CostFunction.`
   * <p>
   * The members of this class represent weight deltas computed by
   * backpropagation.
   *
   * @see Classifier.CostFunction
   */
  public class Cost {

    private double cost;

    // Percent of training examples predicted correctly
    private double percentCorrect;

    // Weight deltas
    private final double[][] gradW1;
    private final double[] gradb1;
    private final double[][] gradW2;
    private final double[][] gradE;

    private Cost(double cost, double percentCorrect, double[][] gradW1, double[] gradb1, double[][] gradW2,
                 double[][] gradE) {
      this.cost = cost;
      this.percentCorrect = percentCorrect;

      this.gradW1 = gradW1;
      this.gradb1 = gradb1;
      this.gradW2 = gradW2;
      this.gradE = gradE;
    }

    /**
     * Merge the given {@code Cost} data with the data in this
     * instance.
     *
     * @param otherCost
     */
    public void merge(Cost otherCost) {
      this.cost += otherCost.getCost();
      this.percentCorrect += otherCost.getPercentCorrect();

      ArrayMath.addInPlace(gradW1, otherCost.getGradW1());
      ArrayMath.pairwiseAddInPlace(gradb1, otherCost.getGradb1());
      ArrayMath.addInPlace(gradW2, otherCost.getGradW2());
      ArrayMath.addInPlace(gradE, otherCost.getGradE());
    }

    /**
     * Backpropagate gradient values from gradSaved into the gradients
     * for the E vectors that generated them.
     *
     * @param featuresSeen Feature IDs observed during training for
     *                     which gradSaved values need to be backprop'd
     *                     into gradE
     */
    private void backpropSaved(Set<Integer> featuresSeen) {
      for (int x : featuresSeen) {
        int mapX = preMap.get(x);
        int tok = x / config.numTokens;
        int offset = (x % config.numTokens) * config.embeddingSize;
        for (int j = 0; j < config.hiddenSize; ++j) {
          double delta = gradSaved[mapX][j];
          for (int k = 0; k < config.embeddingSize; ++k) {
            gradW1[j][offset + k] += delta * E[tok][k];
            gradE[tok][k] += delta * W1[j][offset + k];
          }
        }
      }
    }

    /**
     * Add L2 regularization cost to the gradients associated with this
     * instance.
     */
    private void addL2Regularization(double regularizationWeight) {
      for (int i = 0; i < W1.length; ++i) {
        for (int j = 0; j < W1[i].length; ++j) {
          cost += regularizationWeight * W1[i][j] * W1[i][j] / 2.0;
          gradW1[i][j] += regularizationWeight * W1[i][j];
        }
      }

      for (int i = 0; i < b1.length; ++i) {
        cost += regularizationWeight * b1[i] * b1[i] / 2.0;
        gradb1[i] += regularizationWeight * b1[i];
      }

      for (int i = 0; i < W2.length; ++i) {
        for (int j = 0; j < W2[i].length; ++j) {
          cost += regularizationWeight * W2[i][j] * W2[i][j] / 2.0;
          gradW2[i][j] += regularizationWeight * W2[i][j];
        }
      }

      for (int i = 0; i < E.length; ++i) {
        for (int j = 0; j < E[i].length; ++j) {
          cost += regularizationWeight * E[i][j] * E[i][j] / 2.0;
          gradE[i][j] += regularizationWeight * E[i][j];
        }
      }
    }

    public double getCost() {
      return cost;
    }

    public double getPercentCorrect() {
      return percentCorrect;
    }

    public double[][] getGradW1() {
      return gradW1;
    }

    public double[] getGradb1() {
      return gradb1;
    }

    public double[][] getGradW2() {
      return gradW2;
    }

    public double[][] getGradE() {
      return gradE;
    }

  }

  /**
   * Determine the feature IDs which need to be pre-computed for
   * training with these examples.
   */
  private Set<Integer> getToPreCompute(List<Example> examples) {
    Set<Integer> featureIDs = new HashSet<>();
    for (Example ex : examples) {
      List<Integer> feature = ex.getFeature();

      for (int j = 0; j < config.numTokens; j++) {
        int tok = feature.get(j);
        int index = tok * config.numTokens + j;
        if (preMap.containsKey(index))
          featureIDs.add(index);
      }
    }

    double percentagePreComputed = featureIDs.size() / (float) config.numPreComputed;
    log.info(String.format("Percent actually necessary to pre-compute: %f%%%n", percentagePreComputed * 100));

    return featureIDs;
  }

  /**
   * Determine the total cost on the dataset associated with this
   * classifier using the current learned parameters. This cost is
   * evaluated using mini-batch adaptive gradient descent.
   *
   * This method launches multiple threads, each of which evaluates
   * training cost on a partition of the mini-batch.
   *
   * @param batchSize
   * @param regParameter Regularization parameter (lambda)
   * @param dropOutProb Drop-out probability. Hidden-layer units in the
   *                    neural network will be randomly turned off
   *                    while training a particular example with this
   *                    probability.
   * @return A {@link edu.stanford.nlp.parser.nndep.Classifier.Cost}
   *         object which describes the total cost of the given
   *         weights, and includes gradients to be used for further
   *         training
   */
  public Cost computeCostFunction(int batchSize, double regParameter, double dropOutProb) {
    validateTraining();

    List<Example> examples = Util.getRandomSubList(dataset.examples, batchSize);

    // Redo precomputations for only those features which are triggered
    // by examples in this mini-batch.
    Set<Integer> toPreCompute = getToPreCompute(examples);
    preCompute(toPreCompute);

    // Set up parameters for feedforward
    FeedforwardParams params = new FeedforwardParams(batchSize, dropOutProb);

    // Zero out saved-embedding gradients
    gradSaved = new double[preMap.size()][config.hiddenSize];

    int numChunks = config.trainingThreads;
    List<List<Example>> chunks = CollectionUtils.partitionIntoFolds(examples, numChunks);

    // Submit chunks for processing on separate threads
    for (Collection<Example> chunk : chunks)
      jobHandler.put(new Pair<>(chunk, params));
    jobHandler.join(false);

    // Join costs from each chunk
    Cost cost = null;
    while (jobHandler.peek()) {
      Cost otherCost = jobHandler.poll();

      if (cost == null)
        cost = otherCost;
      else
        cost.merge(otherCost);
    }

    if (cost == null)
      return null;

    // Backpropagate gradients on saved pre-computed values to actual
    // embeddings
    cost.backpropSaved(toPreCompute);

    cost.addL2Regularization(regParameter);

    return cost;
  }

  /**
   * Update classifier weights using the given training cost
   * information.
   *
   * @param cost Cost information as returned by
   *             {@link #computeCostFunction(int, double, double)}.
   * @param adaAlpha Global AdaGrad learning rate
   * @param adaEps Epsilon value for numerical stability in AdaGrad's
   *               division
   */
  public void takeAdaGradientStep(Cost cost, double adaAlpha, double adaEps) {
    validateTraining();

    double[][] gradW1 = cost.getGradW1(), gradW2 = cost.getGradW2(),
        gradE = cost.getGradE();
    double[] gradb1 = cost.getGradb1();

    for (int i = 0; i < W1.length; ++i) {
      for (int j = 0; j < W1[i].length; ++j) {
        eg2W1[i][j] += gradW1[i][j] * gradW1[i][j];
        W1[i][j] -= adaAlpha * gradW1[i][j] / Math.sqrt(eg2W1[i][j] + adaEps);
      }
    }

    for (int i = 0; i < b1.length; ++i) {
      eg2b1[i] += gradb1[i] * gradb1[i];
      b1[i] -= adaAlpha * gradb1[i] / Math.sqrt(eg2b1[i] + adaEps);
    }

    for (int i = 0; i < W2.length; ++i) {
      for (int j = 0; j < W2[i].length; ++j) {
        eg2W2[i][j] += gradW2[i][j] * gradW2[i][j];
        W2[i][j] -= adaAlpha * gradW2[i][j] / Math.sqrt(eg2W2[i][j] + adaEps);
      }
    }

    if (config.doWordEmbeddingGradUpdate) {
      for (int i = 0; i < E.length; ++i) {
        for (int j = 0; j < E[i].length; ++j) {
          eg2E[i][j] += gradE[i][j] * gradE[i][j];
          E[i][j] -= adaAlpha * gradE[i][j] / Math.sqrt(eg2E[i][j] + adaEps);
        }
      }
    }
  }

  private void initGradientHistories() {
    eg2E = new double[E.length][E[0].length];
    eg2W1 = new double[W1.length][W1[0].length];
    eg2b1 = new double[b1.length];
    eg2W2 = new double[W2.length][W2[0].length];
  }

  /**
   * Clear all gradient histories used for AdaGrad training.
   *
   * @throws java.lang.IllegalStateException If not training
   */
  public void clearGradientHistories() {
    validateTraining();
    initGradientHistories();
  }

  private void validateTraining() {
    if (!isTraining)
      throw new IllegalStateException("Not training, or training was already finalized");
  }

  /**
   * Finish training this classifier; prepare for a shutdown.
   */
  public void finalizeTraining() {
    validateTraining();

    // Destroy threadpool
    jobHandler.join(true);

    isTraining = false;
  }

  /**
   * @see #preCompute(java.util.Set)
   */
  public void preCompute() {
    preCompute(preMap.keySet());
  }

  /**
   * Pre-compute hidden layer activations for some set of possible
   * feature inputs.
   *
   * @param toPreCompute Set of feature IDs for which hidden layer
   *                     activations should be precomputed
   */
  public void preCompute(Set<Integer> toPreCompute) {
    long startTime = System.currentTimeMillis();

    // NB: It'd make sense to just make the first dimension of this
    // array the same size as `toPreCompute`, then recalculate all
    // `preMap` indices to map into this denser array. But this
    // actually hurt training performance! (See experiments with
    // "smallMap.")
    saved = new double[preMap.size()][config.hiddenSize];
    final int numTokens = config.numTokens;
    final int embeddingSize = config.embeddingSize;

    for (int x : toPreCompute) {
      int mapX = preMap.get(x);
      int tok = x / numTokens;
      int pos = x % numTokens;
      matrixMultiplySliceSum(saved[mapX], W1, E[tok], pos * embeddingSize);
    }
    log.info("PreComputed " + toPreCompute.size() + " vectors, elapsed Time: " +
            (System.currentTimeMillis() - startTime) / 1000.0 + " sec");
  }


  double[] computeScores(int[] feature) {
    return computeScores(feature, preMap);
  }

  /**
   * Feed a feature vector forward through the network. Returns the
   * values of the output layer.
   */
  private double[] computeScores(int[] feature, Map<Integer, Integer> preMap) {
    final double[] hidden = new double[config.hiddenSize];
    final int numTokens = config.numTokens;
    final int embeddingSize = config.embeddingSize;

    int offset = 0;
    for (int j = 0; j < feature.length; j++) {
      int tok = feature[j];
      int index = tok * numTokens + j;
      Integer idInteger = preMap.get(index);
      if (idInteger != null) {
        ArrayMath.pairwiseAddInPlace(hidden, saved[idInteger]);
      } else {
        if (isTraining || config.numCached == 0) {
          // TODO: can the cache be used when training, actually?
          matrixMultiplySliceSum(hidden, W1, E[tok], offset);
        } else {
          float[] cached;
          synchronized (cache) {
            cached = cache.getOrDefault(index, null);
          }
          if (cached == null) {
            cached = matrixMultiplySlice(W1, E[tok], offset);
            synchronized (cache) {
              cache.add(index, cached);
            }
          }
          ArrayMath.pairwiseAddInPlace(hidden, cached);
        }
      }
      offset += embeddingSize;
    }
    addCubeInPlace(hidden, b1);
    return matrixMultiply(W2, hidden);
  }

  // extracting these small methods makes things faster; hotspot likes them

  private static double[] matrixMultiply(double[][] matrix, float[] vector) {
    double[] result = new double[matrix.length];
    for (int i = 0; i < matrix.length; i++) {
      result[i] = ArrayMath.dotProduct(matrix[i], vector);
    }
    return result;
  }

  private static float[] matrixMultiplySlice(float[][] matrix, float[] vector, int leftColumnOffset) {
    float[] slice = new float[matrix.length];
    for (int i = 0; i < matrix.length; i++) {
      double partial = 0.0;
      for (int j = 0; j < vector.length; j++) {
        partial += matrix[i][leftColumnOffset + j] * vector[j];
      }
      slice[i] = (float) partial;
    }
    return slice;
  }

  private static double[] matrixMultiply(float[][] matrix, double[] vector) {
    double[] result = new double[matrix.length];
    for (int i = 0; i < matrix.length; i++) {
      result[i] = ArrayMath.dotProduct(vector, matrix[i]);
    }
    return result;
  }

  private static void matrixMultiplySliceSum(double[] sum, float[][] matrix, float[] vector, int leftColumnOffset) {
    for (int i = 0; i < matrix.length; i++) {
      double partial = sum[i];
      for (int j = 0; j < vector.length; j++) {
        partial += matrix[i][leftColumnOffset + j] * vector[j];
      }
      sum[i] = partial;
    }
  }

  private static void addCubeInPlace(double[] vector, float [] bias) {
    for (int i = 0; i < vector.length; i++) {
      vector[i] += bias[i]; // add bias
      vector[i] = vector[i] * vector[i] * vector[i];  // cube nonlinearity
    }
  }


  public float[][] getW1() {
    return W1;
  }

  public float[] getb1() {
    return b1;
  }

  public float[][] getW2() {
    return W2;
  }

  public float[][] getE() {
    return E;
  }

}
