
/* 
* 	@Author:  Danqi Chen
* 	@Email:  danqi@cs.stanford.edu
*	@Created:  2014-08-25
* 	@Last Modified:  2014-10-05
*/

package edu.stanford.nlp.parser.nndep;

import edu.stanford.nlp.parser.nndep.util.Util;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

public class Classifier 
{
  // E: numFeatures x embeddingSize
  // W1: hiddenSize x (embeddingSize x numFeatures)
  // b1: hiddenSize
  // W2: numLabels x hiddenSize

  // Weight matrices
  private final double[][] W1, W2, E;
  private final double[] b1;

  // Gradient histories
  private final double[][] eg2W1, eg2W2, eg2E;
  double[] eg2b1;

  // Pre-computed hidden unit activations
  private double[][] saved;

  /**
   * TODO document
   */
  private final Map<Integer, Integer> preMap;

  /**
   * A subset of {@code preMap} containing only features necessary for
   * the given mini-batch.
   */
  private Map<Integer, Integer> smallMap;

  private final boolean isTraining;

  /**
   * All training examples.
   */
  private final Dataset dataset;

  /**
   * We use MulticoreWrapper to parallelize mini-batch training.
   *
   * Threaded job input: partition of minibatch;
   *   current weights + params
   * Threaded job output: cost value, weight gradients for partition of
   *   minibatch
   */
  private final MulticoreWrapper<Pair<Collection<Example>, FeedforwardParams>, Cost> jobHandler;

  private final Config config;

  /**
   * TODO document
   */
  private final int numLabels;

  public Classifier(Config config, Dataset dataset, double[][] E, double[][] W1, double[] b1, double[][] W2) {
    this(config, dataset, E, W1, b1, W2, new ArrayList<>());
  }

  public Classifier(Config config, double[][] E, double[][] W1, double[] b1, double[][] W2, List<Integer> preComputed) {
    this(config, null, E, W1, b1, W2, preComputed);
  }

  public Classifier(Config config, Dataset dataset, double[][] E, double[][] W1, double[] b1, double[][] W2,
                    List<Integer> preComputed) {
    this.config = config;
    this.dataset = dataset;

    this.E = E;
    this.W1 = W1;
    this.b1 = b1;
    this.W2 = W2;

    numLabels = W2.length;

    eg2E = new double[E.length][E[0].length];
    eg2W1 = new double[W1.length][W1[0].length];
    eg2b1 = new double[b1.length];
    eg2W2 = new double[W2.length][W2[0].length];

    preMap = new HashMap<>();
    for (int i = 0; i < preComputed.size(); ++i)
      preMap.put(preComputed.get(i), i);

    isTraining = dataset != null;
    if (isTraining)
      jobHandler = new MulticoreWrapper<>(config.trainingThreads, new CostFunction(), false);
    else
      jobHandler = null;
  }

  private class CostFunction implements ThreadsafeProcessor<Pair<Collection<Example>, FeedforwardParams>, Cost> {

    private double[][] gradW1;
    private double[] gradb1;
    private double[][] gradW2;
    private double[][] gradE;
    private double[][] gradSaved;

    private final ThreadLocalRandom random = ThreadLocalRandom.current();

    @Override
    public Cost process(Pair<Collection<Example>, FeedforwardParams> input) {
      Collection<Example> examples = input.first();
      FeedforwardParams params = input.second();

      gradW1 = new double[params.getW1().length][params.getW1()[0].length];
      gradb1 = new double[params.getB1().length];
      gradW2 = new double[params.getW2().length][params.getW2()[0].length];
      gradE = new double[params.getE().length][params.getE()[0].length];
      gradSaved = new double[smallMap.size()][config.hiddenSize];

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

          if (smallMap.containsKey(index)) {
            // Unit activations for this input feature value have been
            // precomputed
            int id = smallMap.get(index);

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

        cost += (Math.log(sum2) - Math.log(sum1)) / examples.size();
        if (label.get(optLabel) == 1)
          correct += +1.0 / examples.size();

        double[] gradHidden3 = new double[config.hiddenSize];
        for (int i = 0; i < numLabels; ++i)
          if (label.get(i) >= 0) {
            double delta = -(label.get(i) - scores[i] / sum2) / examples.size();
            for (int nodeIndex : ls) {
              gradW2[i][nodeIndex] += delta * hidden3[nodeIndex];
              gradHidden3[nodeIndex] += delta * W2[i][nodeIndex];
            }
          }

        double[] gradHidden = new double[config.hiddenSize];
        for (int nodeIndex : ls) {
          gradHidden[nodeIndex] = gradHidden3[nodeIndex] * 3 * hidden[nodeIndex] * hidden[nodeIndex];
          gradb1[nodeIndex] += gradHidden3[nodeIndex];
        }

        offset = 0;
        for (int j = 0; j < config.numTokens; ++j) {
          int tok = feature.get(j);
          int index = tok * config.numTokens + j;
          if (smallMap.containsKey(index)) {
            int id = smallMap.get(index);
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

      double reg = params.getRegParameter();
      for (int i = 0; i < W1.length; ++i)
        for (int j = 0; j < W1[i].length; ++j) {
          cost += reg * W1[i][j] * W1[i][j] / 2.0;
          gradW1[i][j] += reg * W1[i][j];
        }

      for (int i = 0; i < b1.length; ++i) {
        cost += reg * b1[i] * b1[i] / 2.0;
        gradb1[i] += reg * b1[i];
      }

      for (int i = 0; i < W2.length; ++i)
        for (int j = 0; j < W2[i].length; ++j) {
          cost += reg * W2[i][j] * W2[i][j] / 2.0;
          gradW2[i][j] += reg * W2[i][j];
        }

      for (int i = 0; i < E.length; ++i)
        for (int j = 0; j < E[i].length; ++j) {
          cost += reg * E[i][j] * E[i][j] / 2.0;
          gradE[i][j] += reg * E[i][j];
        }

      return new Cost(cost, correct, gradW1, gradb1, gradW2, gradE, gradSaved);
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
     * Weight matrices corresponding to those in the outer classifier
     * instance
     */
    private final double[][] W1;
    private final double[] b1;
    private final double[][] W2;
    private final double[][] E;
    private final double[][] saved;

    private final double regParameter;
    private final double dropOutProb;

    private FeedforwardParams(double regParameter, double dropOutProb, double[][] W1, double[] b1, double[][] W2,
                              double[][] E, double[][] saved) {
      this.dropOutProb = dropOutProb;
      this.regParameter = regParameter;
      this.saved = saved;
      this.E = E;
      this.W2 = W2;
      this.b1 = b1;
      this.W1 = W1;
    }

    public double[][] getW1() {
      return W1;
    }

    public double[] getB1() {
      return b1;
    }

    public double[][] getW2() {
      return W2;
    }

    public double[][] getE() {
      return E;
    }

    public double[][] getSaved() {
      return saved;
    }

    public double getRegParameter() {
      return regParameter;
    }

    public double getDropOutProb() {
      return dropOutProb;
    }
  }

  /**
   * Describes the result of feedforward + backpropagation through
   * the neural network for the batch provided to a `CostFunction.`
   *
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
    private final double[][] gradSaved;

    private Cost(double cost, double percentCorrect, double[][] gradW1, double[] gradb1, double[][] gradW2,
                 double[][] gradE, double[][] gradSaved) {
      this.cost = cost;
      this.percentCorrect = percentCorrect;

      this.gradW1 = gradW1;
      this.gradb1 = gradb1;
      this.gradW2 = gradW2;
      this.gradE = gradE;
      this.gradSaved = gradSaved;
    }

    /**
     * Merge the given {@code Cost} data with the data in this
     * instance.
     *
     * @param otherCost
     */
    public void merge(Cost otherCost) {
      this.cost += otherCost.getCost();

      addInPlace(gradW1, otherCost.getGradW1());
      addInPlace(gradb1, otherCost.getGradb1());
      addInPlace(gradW2, otherCost.getGradW2());
      addInPlace(gradE, otherCost.getGradE());
      addInPlace(gradSaved, otherCost.getGradSaved());
    }

    /**
     * Backpropagate gradient values from gradSaved into the gradients
     * for the E vectors that generated them.
     */
    public void backpropSaved(Map<Integer, Integer> preMap) {
      for (int x : preMap.keySet()) {
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

    public double[][] getGradSaved() {
      return gradSaved;
    }

  }

  /**
   * Determine the tokens which need to be pre-computed in order to
   * train this mini-batch of examples.
   */
  private Set<Integer> getPreComputeTokens(List<Example> examples) {
    Set<Integer> ret = new HashSet<>();
    for (Example ex : examples) {
      List<Integer> feature = ex.getFeature();

      for (int j = 0; j < config.numTokens; j++) {
        int tok = feature.get(j);
        int index = tok * config.numTokens + j;
        if (preMap.containsKey(index))
          ret.add(index);
      }
    }

    return ret;
  }

  public Cost computeCostFunction(int batchSize, double regParameter, double dropOutProb) {
    List<Example> examples = Util.getRandomSubList(dataset.examples, batchSize);

    Set<Integer> toPreCompute = getPreComputeTokens(examples);
    double percentagePreComputed = toPreCompute.size() / (float) config.numPreComputed;
    System.err.printf("Percent actually necessary to pre-compute: %f%%%n", percentagePreComputed * 100);

    smallMap = new HashMap<>(toPreCompute.size());
    int newId = 0;
    for (int id : toPreCompute) {
      smallMap.put(preMap.get(id), newId);
      newId++;
    }

    preCompute(smallMap);

    // Set up parameters for feedforward
    FeedforwardParams params = new FeedforwardParams(regParameter, dropOutProb, W1, b1, W2, E, saved);

    int numChunks = config.trainingThreads;
    List<Collection<Example>> chunks = CollectionUtils.partitionIntoFolds(examples, numChunks);

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
    cost.backpropSaved(smallMap);

    return cost;
  }

	public void takeAdaGradientStep(Cost cost, double adaAlpha, double adaEps)
	{
    double[][] gradW1 = cost.getGradW1(), gradW2 = cost.getGradW2(),
        gradE = cost.getGradE(), gradSaved = cost.getGradSaved();
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

    for (int i = 0; i < E.length; ++i) {
      for (int j = 0; j < E[i].length; ++j) {
        eg2E[i][j] += gradE[i][j] * gradE[i][j];
        E[i][j] -= adaAlpha * gradE[i][j] / Math.sqrt(eg2E[i][j] + adaEps);
      }
    }
  }

  public void preCompute() {
    preCompute(preMap);
  }

  public void preCompute(Map<Integer, Integer> preMap) {
    long startTime = System.currentTimeMillis();
    saved = new double[preMap.size()][config.hiddenSize];
    for (int x : preMap.keySet()) {
      int mapX = preMap.get(x);
      int tok = x / config.numTokens;
      int pos = x % config.numTokens;
      for (int j = 0; j < config.hiddenSize; ++j)
        for (int k = 0; k < config.embeddingSize; ++k)
          saved[mapX][j] += W1[j][pos * config.embeddingSize + k] * E[tok][k];
    }
    System.out.println("PreComputed " + preMap.size() + ", Elapsed Time: " + (System
        .currentTimeMillis() - startTime) / 1000.0 + " (s)");
  }

  public double[] computeScores(List<Integer> feature) {
    return computeScores(feature, preMap);
  }

  /**
   * Feed a feature vector forward through the network. Returns the
   * values of the output layer.
   */
  public double[] computeScores(List<Integer> feature, Map<Integer, Integer> preMap) {
    double[] scores = new double[numLabels];
    double[] hidden = new double[config.hiddenSize];
    int offset = 0;
    for (int j = 0; j < config.numTokens; ++j) {
      int tok = feature.get(j);
      int index = tok * config.numTokens + j;
      if (preMap.containsKey(index)) {
        int id = preMap.get(index);
        for (int i = 0; i < config.hiddenSize; ++i)
          hidden[i] += saved[id][i];
      } else {
        for (int i = 0; i < config.hiddenSize; ++i)
          for (int k = 0; k < config.embeddingSize; ++k)
            hidden[i] += W1[i][offset + k] * E[tok][k];
      }
      offset += config.embeddingSize;
    }

    for (int i = 0; i < config.hiddenSize; ++i) {
      hidden[i] += b1[i];
      hidden[i] = hidden[i] * hidden[i] * hidden[i];
    }

    for (int i = 0; i < numLabels; ++i)
      for (int j = 0; j < config.hiddenSize; ++j)
        scores[i] += W2[i][j] * hidden[j];
    return scores;
  }

  public double[][] getW1() {
    return W1;
  }

  public double[] getb1() {
    return b1;
  }

  public double[][] getW2() {
    return W2;
  }

  public double[][] getE() {
    return E;
  }

  /**
   * Add the two 2d arrays in place of {@code m1}.
   *
   * @throws java.lang.IndexOutOfBoundsException (possibly) If
   *         {@code m1} and {@code m2} are not of the same dimensions
   */
  private static void addInPlace(double[][] m1, double[][] m2) {
    for (int i = 0; i < m1.length; i++)
      for (int j = 0; j < m1[0].length; j++)
        m1[i][j] += m2[i][j];
  }

  /**
   * Add the two 1d arrays in place of {@code a1}.
   *
   * @throws java.lang.IndexOutOfBoundsException (Possibly) if
   *         {@code a1} and {@code a2} are not of the same dimensions
   */
  private static void addInPlace(double[] a1, double[] a2) {
    for (int i = 0; i < a1.length; i++)
      a1[i] += a2[i];
  }

}
