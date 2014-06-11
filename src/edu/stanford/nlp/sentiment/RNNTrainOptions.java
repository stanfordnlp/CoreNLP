package edu.stanford.nlp.sentiment;

import java.io.Serializable;

public class RNNTrainOptions implements Serializable {
  public int batchSize = 20;

  /** Number of times through all the trees */
  public int epochs = 1000;

  public int debugOutputSeconds = 60 * 20;

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

  /** Regularization cost for the transform matrices and tensors */
  public double regTransform = 0.001;
  
  /** Regularization cost for the classification matrices */
  public double regClassification = 0.0001;

  /** Regularization cost for the word vectors */
  public double regWordVector = 0.0001;

  public int setOption(String[] args, int argIndex) {
    if (args[argIndex].equalsIgnoreCase("-batchSize")) {
      batchSize = Integer.valueOf(args[argIndex + 1]);
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-epochs")) {
      epochs = Integer.valueOf(args[argIndex + 1]);
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-debugOutputSeconds")) {
      debugOutputSeconds = Integer.valueOf(args[argIndex + 1]);
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-maxTrainTimeSeconds")) {
      maxTrainTimeSeconds = Integer.valueOf(args[argIndex + 1]);
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-learningRate")) {
      learningRate = Double.valueOf(args[argIndex + 1]);
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-scalingForInit")) {
      scalingForInit = Double.valueOf(args[argIndex + 1]);
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-regTransform")) {
      regTransform = Double.valueOf(args[argIndex + 1]);
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-regClassification")) {
      regClassification = Double.valueOf(args[argIndex + 1]);
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-regWordVector")) {
      regWordVector = Double.valueOf(args[argIndex + 1]);
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-classWeights")) {
      String classWeightString = args[argIndex + 1];
      String[] pieces = classWeightString.split(",");
      classWeights = new double[pieces.length];
      for (int i = 0; i < pieces.length; ++i) {
        classWeights[i] = Double.valueOf(pieces[i]);
      }
      return argIndex + 2;
    } else {
      return argIndex;
    }
  }
}
