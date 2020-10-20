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
    EARLY_TERMINATION, GOLD, ORACLE, REORDER_ORACLE, BEAM, REORDER_BEAM;
  };
  public TrainingMethod trainingMethod = TrainingMethod.EARLY_TERMINATION;

  public static final int DEFAULT_BEAM_SIZE = 4;
  public int beamSize = 0;
  
  /** How many times a feature must be seen when training.  Less than this and it is filtered. */
  public int featureFrequencyCutoff = 0;

  /** Saves intermediate models, but that takes up a lot of space */
  public boolean saveIntermediateModels = false;

  /** If we cut off features with featureFrequencyCutoff, this retrains with only the existing features */
  public boolean retrainAfterCutoff = true;

  /** Does not seem to help... perhaps there is a logic bug in how to compensate for missed binary transitions */
  public boolean oracleShiftToBinary = false;

  /** Does help, but makes the models much bigger for a miniscule gain */
  public boolean oracleBinaryToShift = false;

  /** If positive, every 10 iterations, multiply the learning rate by this amount. */
  public double decayLearningRate = 0.0;

  // version id randomly chosen by forgetting to set the version id when serializing models
  private static final long serialVersionUID = -8158249539308373819L;
}
