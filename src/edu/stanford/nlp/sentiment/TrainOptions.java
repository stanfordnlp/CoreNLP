package edu.stanford.nlp.sentiment;

import java.io.Serializable;

public class TrainOptions implements Serializable {
  int batchSize;

  int iterations;

  int debugOutputSeconds;

  int maxTrainTimeSeconds;

  double learningRate;
}
