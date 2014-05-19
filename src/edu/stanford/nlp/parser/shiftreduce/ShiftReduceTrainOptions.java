package edu.stanford.nlp.parser.shiftreduce;

import edu.stanford.nlp.parser.lexparser.TrainOptions;

public class ShiftReduceTrainOptions extends TrainOptions {
  /** 
   * If set to 0, training outputs the last model produced, regardless
   * of its score.  Otherwise it takes the best k models and averages
   * them together.
   */
  public int averagedModels = 8;

  /**
   * Cross-validate over the number of models to average, using the
   * dev set, to figure out which number between 1 and averagedModels
   * we actually want to use
   */
  public boolean cvAveragedModels = true;

  public enum TrainingMethod {
    EARLY_TERMINATION, GOLD, ORACLE, BEAM;
  };
  public TrainingMethod trainingMethod = TrainingMethod.EARLY_TERMINATION;

  public int beamSize = 1;
}
