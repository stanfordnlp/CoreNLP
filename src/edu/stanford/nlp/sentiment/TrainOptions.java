package edu.stanford.nlp.sentiment;

import java.io.Serializable;

public class TrainOptions implements Serializable {
  public int batchSize;

  public int iterations;

  public int debugOutputSeconds;

  public int maxTrainTimeSeconds;

  public double learningRate;

  public double scalingForInit;
}
