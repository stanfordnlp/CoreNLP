package edu.stanford.nlp.sentiment;

import java.io.Serializable;

public class RNNTrainOptions implements Serializable {
  public int batchSize = 20;

  /** Number of times through all the trees */
  public int epochs = 1000;

  public int debugOutputSeconds = 60 * 20;

  public int maxTrainTimeSeconds = 60 * 60 * 5;

  public double learningRate = 0.01;

  public double scalingForInit = 1.0;

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
    } else {
      return argIndex;
    }
  }
}
