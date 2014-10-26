
/*
* 	@Author:  Danqi Chen
* 	@Email:  danqi@cs.stanford.edu
*	@Created:  2014-10-03
* 	@Last Modified:  2014-10-05
*/

package edu.stanford.nlp.parser.nndep;

public class Config
{
  /**
   * Refuse to train on words which have a corpus frequency less than
   * this number.
   */
  public static int wordCutOff = 1;

  /**
   * Model weights will be initialized to random values within the
   * range {@code [-initRange, initRange]}.
   */
  public static double initRange = 0.01;

  /**
   * Maximum number of iterations for training
   */
  public static int maxIter = 20000;

  /**
   * Size of mini-batch for training. A random subset of training
   * examples of this size will be used to train the classifier on each
   * iteration.
   */
  public static int batchSize = 10000;

  /**
   * An epsilon value added to the denominator of the AdaGrad
   * expression for numerical stability
   */
  public static double adaEps = 1e-6;

  /**
   * Initial global learning rate for AdaGrad training
   */
  public static double adaAlpha = 0.01;

  /**
   * Regularization parameter. All weight updates are scaled by this
   * single parameter.
   */
  public static double regParameter = 1e-8;

  /**
   * Dropout probability. For each training example we randomly choose
   * some amount of units to disable in the neural network classifier.
   * This probability controls the proportion of units "dropped out."
   */
  public static double dropProb = 0.5;

  /**
   * Size of the neural network hidden layer.
   */
  public static int hiddenSize = 200;

  /**
   * Dimensionality of the word embeddings used
   */
  public static int embeddingSize = 50;

  /**
   * Total number of tokens provided as input to the classifier. (Each
   * token is provided in word embedding form.)
   */
  public static int numTokens = 48;

  /**
   * Number of input tokens for which we should compute hidden-layer
   * unit activations.
   */
  public static int numPreComputed = 100000;

  /**
   * During training, run a full UAS evaluation after every
   * {@code evalPerIter} iterations.
   */
  public static int evalPerIter = 100;
}
