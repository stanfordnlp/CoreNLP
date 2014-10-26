
/* 
* 	@Author:  Danqi Chen
* 	@Email:  danqi@cs.stanford.edu
*	@Created:  2014-08-25
* 	@Last Modified:  2014-10-05
*/

package edu.stanford.nlp.depparser.nn;

import edu.stanford.nlp.depparser.util.Util;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;

import java.util.*;

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

  private final Random random;

  private final int embeddingSize, hiddenSize;

  /**
   * TODO document
   */
  private final int numTokens, numLabels;

  /**
   * Number of words for which we have precomputed the hidden-layer
   * unit activation values
   */
  private final int numPreComputed;

  public Classifier(Dataset dataset, double[][] E, double[][] W1, double[] b1, double[][] W2) {
    this(dataset, E, W1, b1, W2, new ArrayList<>());
  }

  public Classifier(double[][] E, double[][] W1, double[] b1, double[][] W2, List<Integer> preComputed) {
    this(null, E, W1, b1, W2, preComputed);
  }

  public Classifier(Dataset dataset, double[][] E, double[][] W1, double[] b1, double[][] W2,
                    List<Integer> preComputed) {
    this.dataset = dataset;

    this.E = E;
    this.W1 = W1;
    this.b1 = b1;
    this.W2 = W2;

    embeddingSize = E[0].length;
    hiddenSize = W1.length;
    numTokens = W1[0].length / embeddingSize;
    numLabels = W2.length;

    eg2E = new double[E.length][E[0].length];
    eg2W1 = new double[W1.length][W1[0].length];
    eg2b1 = new double[b1.length];
    eg2W2 = new double[W2.length][W2[0].length];

    preMap = new HashMap<>();
    numPreComputed = preComputed.size();
    for (int i = 0; i < preComputed.size(); ++i)
      preMap.put(preComputed.get(i), i);

    // TODO make configurable
    int nThreads = Runtime.getRuntime().availableProcessors() - 1;
    jobHandler = new MulticoreWrapper<>(nThreads, new CostFunction(), false);

    random = new Random();
  }

  private class CostFunction implements ThreadsafeProcessor<Pair<Collection<Example>, FeedforwardParams>, Cost> {

    private double[][] gradW1;
    private double[] gradb1;
    private double[][] gradW2;
    private double[][] gradE;
    private double[][] gradSaved;

    @Override
    public Cost process(Pair<Collection<Example>, FeedforwardParams> input) {
      Collection<Example> examples = input.first();
      FeedforwardParams params = input.second();

      gradW1 = new double[params.getW1().length][params.getW1()[0].length];
      gradb1 = new double[params.getB1().length];
      gradW2 = new double[params.getW2().length][params.getW2()[0].length];
      gradE = new double[params.getE().length][params.getE()[0].length];
      gradSaved = new double[numPreComputed][hiddenSize];

      double cost = 0.0;
      double correct = 0.0;
      for (Example ex : examples) {
        List<Integer> feature = ex.getFeature();
        List<Integer> label = ex.getLabel();

        double[] scores = new double[numLabels];
        double[] hidden = new double[hiddenSize];
        double[] hidden3 = new double[hiddenSize];

        // Run dropout: randomly drop some hidden-layer units
        List<Integer> unDropped = new ArrayList<Integer>();
        int numH = 0;
        for (int i = 0; i < hiddenSize; ++i) {
          if (random.nextDouble() > params.getDropOutProb()) {
            numH += 1;
            unDropped.add(i);
          }
        }

        // Unit IDs which are still active
        int[] ls = CollectionUtils.asIntArray(unDropped);

        int offset = 0;
        for (int j = 0; j < numTokens; ++j) {
          int tok = feature.get(j);
          int index = tok * numTokens + j;

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
              for (int k = 0; k < embeddingSize; ++k)
                hidden[nodeIndex] += W1[nodeIndex][offset + k] * E[tok][k];
            }
          }
          offset += embeddingSize;
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

        double[] gradHidden3 = new double[hiddenSize];
        for (int i = 0; i < numLabels; ++i)
          if (label.get(i) >= 0) {
            double delta = -(label.get(i) - scores[i] / sum2) / examples.size();
            for (int lj = 0; lj < numH; ++lj) {
              int j = ls[lj];
              gradW2[i][j] += delta * hidden3[j];
              gradHidden3[j] += delta * W2[i][j];
            }
          }

        double[] gradHidden = new double[hiddenSize];
        for (int li = 0; li < numH; ++li) {
          int i = ls[li];
          gradHidden[i] = gradHidden3[i] * 3 * hidden[i] * hidden[i];
          gradb1[i] += gradHidden3[i];
        }

        offset = 0;
        for (int j = 0; j < numTokens; ++j) {
          int tok = feature.get(j);
          int index = tok * numTokens + j;
          if (preMap.containsKey(index)) {
            int id = preMap.get(index);
            for (int li = 0; li < numH; ++li)
              gradSaved[id][ls[li]] += gradHidden[ls[li]];
          } else {
            for (int li = 0; li < numH; ++li) {
              int i = ls[li];
              for (int k = 0; k < embeddingSize; ++k) {
                gradW1[i][offset + k] += gradHidden[i] * E[tok][k];
                gradE[tok][k] += gradHidden[i] * W1[i][offset + k];
              }
            }
          }
          offset += embeddingSize;
        }
      }

      for (int x : preMap.keySet()) {
        int mapX = preMap.get(x);
        int tok = x / numTokens;
        int offset = (x % numTokens) * embeddingSize;
        for (int j = 0; j < hiddenSize; ++j) {
          double delta = gradSaved[mapX][j];
          for (int k = 0; k < embeddingSize; ++k) {
            gradW1[j][offset + k] += delta * E[tok][k];
            gradE[tok][k] += delta * W1[j][offset + k];
          }
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

      return new Cost(cost, gradW1, gradb1, gradW2, gradE, gradSaved);
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
   * @see edu.stanford.nlp.depparser.nn.Classifier.CostFunction
   */
  public static class Cost {

    private double cost;

    // Weight deltas
    private final double[][] gradW1;
    private final double[] gradb1;
    private final double[][] gradW2;
    private final double[][] gradE;
    private final double[][] gradSaved;

    private Cost(double cost, double[][] gradW1, double[] gradb1, double[][] gradW2, double[][] gradE,
                 double[][] gradSaved) {
      this.cost = cost;
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

    public double getCost() {
      return cost;
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

  public Cost computeCostFunction(int batchSize, double regParameter, double dropOutProb) {
    List<Example> examples = Util.getRandomSubList(dataset.examples, batchSize);
    preCompute();

    // Set up parameters for feedforward
    FeedforwardParams params = new FeedforwardParams(regParameter, dropOutProb, W1, b1, W2, E, saved);

    // TODO make configurable
    int numChunks = Runtime.getRuntime().availableProcessors() - 1;
    List<Collection<Example>> chunks = CollectionUtils.partitionIntoFolds(examples, numChunks);

    // Submit chunks for processing on separate threads
    for (Collection<Example> chunk : chunks)
      jobHandler.put(new Pair<>(chunk, params));
    jobHandler.join();

    // Join costs from each chunk
    Cost cost = null;
    while (jobHandler.peek()) {
      Cost otherCost = jobHandler.poll();

      if (cost == null)
        cost = otherCost;
      else
        cost.merge(otherCost);
    }

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
    long startTime = System.currentTimeMillis();
    saved = new double[numPreComputed][hiddenSize];
    for (int x : preMap.keySet()) {
      int mapX = preMap.get(x);
      int tok = x / numTokens;
      int pos = x % numTokens;
      for (int j = 0; j < hiddenSize; ++j)
        for (int k = 0; k < embeddingSize; ++k)
          saved[mapX][j] += W1[j][pos * embeddingSize + k] * E[tok][k];
    }
    System.out.println("PreComputed " + numPreComputed + ", Elapsed Time: " + (System
        .currentTimeMillis() - startTime) / 1000.0 + " (s)");
  }

  /**
   * Feed a feature vector forward through the network. Returns the
   * values of the output layer.
   */
  public double[] computeScores(List<Integer> feature) {
    double[] scores = new double[numLabels];
    double[] hidden = new double[hiddenSize];
    int offset = 0;
    for (int j = 0; j < numTokens; ++j) {
      int tok = feature.get(j);
      int index = tok * numTokens + j;
      if (preMap.containsKey(index)) {
        int id = preMap.get(index);
        for (int i = 0; i < hiddenSize; ++i)
          hidden[i] += saved[id][i];
      } else {
        for (int i = 0; i < hiddenSize; ++i)
          for (int k = 0; k < embeddingSize; ++k)
            hidden[i] += W1[i][offset + k] * E[tok][k];
      }
      offset += embeddingSize;
    }

    for (int i = 0; i < hiddenSize; ++i) {
      hidden[i] += b1[i];
      hidden[i] = hidden[i] * hidden[i] * hidden[i];
    }

    for (int i = 0; i < numLabels; ++i)
      for (int j = 0; j < hiddenSize; ++j)
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
