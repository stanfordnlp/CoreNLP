package edu.stanford.nlp.sentiment;

import java.io.Serializable;

public class RNNTrainOptions implements Serializable {

  public int batchSize = 27;

  /** Number of times through all the trees */
  public int epochs = 400;

  public int debugOutputEpochs = 8;

  public int maxTrainTimeSeconds = 60 * 60 * 24;

  public double learningRate = 0.01;

  public double scalingForInit = 1.0;

  private double[] classWeights = null;

  /**
   * The classWeights can be passed in as a comma separated list of
   * weights using the -classWeights flag.  If the classWeights are
   * not specified, the value is assumed to be 1.0.  classWeights only
   * apply at train time; we do not weight the classes at all during
   * test time.
   */
  public double getClassWeight(int i) {
    if (classWeights == null) {
      return 1.0;
    }
    return classWeights[i];
  }

  /** Regularization cost for the transform matrix  */
  public double regTransformMatrix = 0.001;

  /** Regularization cost for the classification matrices */
  public double regClassification = 0.0001;

  /** Regularization cost for the word vectors */
  public double regWordVector = 0.0001;

  /**
   * The value to set the learning rate for each parameter when initializing adagrad.
   */
  public double initialAdagradWeight = 0.0;

  /**
   * How many epochs between resets of the adagrad learning rates.
   * Set to 0 to never reset.
   */
  public int adagradResetFrequency = 1;

  /** Regularization cost for the transform tensor  */
  public double regTransformTensor = 0.001;

  /** 
   * Shuffle matrices when training.  Usually should be true.  Set to
   * false to compare training across different implementations, such
   * as with the original Matlab version 
   */
  public boolean shuffleMatrices = true;

  /**
   * If set, the initial matrices are logged to this location as a single file
   * using SentimentModel.toString()
   */
  public String initialMatrixLogPath = null;

  public int nThreads = 1;

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    result.append("TRAIN OPTIONS\n");
    result.append("batchSize=" + batchSize + "\n");
    result.append("epochs=" + epochs + "\n");
    result.append("debugOutputEpochs=" + debugOutputEpochs + "\n");
    result.append("maxTrainTimeSeconds=" + maxTrainTimeSeconds + "\n");
    result.append("learningRate=" + learningRate + "\n");
    result.append("scalingForInit=" + scalingForInit + "\n");
    if (classWeights == null) {
      result.append("classWeights=null\n");
    } else {
      result.append("classWeights=");
      result.append(classWeights[0]);
      for (int i = 1; i < classWeights.length; ++i) {
        result.append("," + classWeights[i]);
      }
      result.append("\n");
    }
    result.append("regTransformMatrix=" + regTransformMatrix + "\n");
    result.append("regTransformTensor=" + regTransformTensor + "\n");
    result.append("regClassification=" + regClassification + "\n");
    result.append("regWordVector=" + regWordVector + "\n");
    result.append("initialAdagradWeight=" + initialAdagradWeight + "\n");
    result.append("adagradResetFrequency=" + adagradResetFrequency + "\n");
    result.append("shuffleMatrices=" + shuffleMatrices + "\n");
    result.append("initialMatrixLogPath=" + initialMatrixLogPath + "\n");
    result.append("nThreads=" + nThreads + "\n");
    return result.toString();
  }

  public int setOption(String[] args, int argIndex) {
    if (args[argIndex].equalsIgnoreCase("-batchSize")) {
      batchSize = Integer.parseInt(args[argIndex + 1]);
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-epochs")) {
      epochs = Integer.parseInt(args[argIndex + 1]);
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-debugOutputEpochs")) {
      debugOutputEpochs = Integer.parseInt(args[argIndex + 1]);
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-maxTrainTimeSeconds")) {
      maxTrainTimeSeconds = Integer.parseInt(args[argIndex + 1]);
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-learningRate")) {
      learningRate = Double.parseDouble(args[argIndex + 1]);
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-scalingForInit")) {
      scalingForInit = Double.parseDouble(args[argIndex + 1]);
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-regTransformMatrix")) {
      regTransformMatrix = Double.parseDouble(args[argIndex + 1]);
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-regTransformTensor")) {
      regTransformTensor = Double.parseDouble(args[argIndex + 1]);
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-regClassification")) {
      regClassification = Double.parseDouble(args[argIndex + 1]);
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-regWordVector")) {
      regWordVector = Double.parseDouble(args[argIndex + 1]);
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-initialAdagradWeight")) {
      initialAdagradWeight = Double.parseDouble(args[argIndex + 1]);
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-adagradResetFrequency")) {
      adagradResetFrequency = Integer.parseInt(args[argIndex + 1]);
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-classWeights")) {
      String classWeightString = args[argIndex + 1];
      String[] pieces = classWeightString.split(",");
      classWeights = new double[pieces.length];
      for (int i = 0; i < pieces.length; ++i) {
        classWeights[i] = Double.parseDouble(pieces[i]);
      }
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-shuffleMatrices")) {
      shuffleMatrices = true;
      return argIndex + 1;
    } else if (args[argIndex].equalsIgnoreCase("-noShuffleMatrices")) {
      shuffleMatrices = false;
      return argIndex + 1;
    } else if (args[argIndex].equalsIgnoreCase("-initialMatrixLogPath")) {
      initialMatrixLogPath = args[argIndex + 1];
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-nThreads") || args[argIndex].equalsIgnoreCase("-numThreads")) {
      nThreads = Integer.parseInt(args[argIndex + 1]);
      return argIndex + 2;
    } else {
      return argIndex;
    }
  }

  private static final long serialVersionUID = 1;
}
