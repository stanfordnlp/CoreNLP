package edu.stanford.nlp.parser.shiftreduce;

import java.util.List;

public class TrainingUpdate {
  final List<String> features;
  final int goldTransition;
  final int predictedTransition;
  final float delta;

  TrainingUpdate(List<String> features, int goldTransition, int predictedTransition, float delta) {
    this.features = features;
    this.goldTransition = goldTransition;
    this.predictedTransition = predictedTransition;
    this.delta = delta;
  }
}
