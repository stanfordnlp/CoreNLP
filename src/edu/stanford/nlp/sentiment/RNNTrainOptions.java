package edu.stanford.nlp.sentiment;

import java.io.Serializable;

public class RNNTrainOptions implements Serializable {
  public int batchSize;

  public int iterations;

  public int debugOutputSeconds;

  public int maxTrainTimeSeconds;

  public double learningRate;

  public double scalingForInit;

  public int setOption(String[] args, int argIndex) {
    if (args[argIndex].equalsIgnoreCase("-batchSize")) {
      batchSize = Integer.valueOf(args[argIndex + 1]);
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-iterations")) {
      iterations = Integer.valueOf(args[argIndex + 1]);
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
