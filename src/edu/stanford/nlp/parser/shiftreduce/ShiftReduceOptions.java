package edu.stanford.nlp.parser.shiftreduce;

import edu.stanford.nlp.parser.lexparser.Options;

public class ShiftReduceOptions extends Options {
  public int beamSize = 1;

  public boolean compoundUnaries = true;

  public String featureFactoryClass = "edu.stanford.nlp.parser.shiftreduce.BasicFeatureFactory";

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

  public String recordBinarized = null;

  public String recordDebinarized = null;

  public enum TrainingMethod {
    EARLY_TERMINATION, GOLD, ORACLE, BEAM;
  };
  public TrainingMethod trainingMethod = TrainingMethod.EARLY_TERMINATION;

  protected int setOptionFlag(String[] args, int i) {
    int j = super.setOptionFlag(args, i);
    if (i != j) {
      return j;
    }
    if (args[i].equalsIgnoreCase("-beamSize")) {
      beamSize = Integer.valueOf(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-compoundUnaries")) {
      compoundUnaries = true;
      i++;
    } else if (args[i].equalsIgnoreCase("-nocompoundUnaries")) {
      compoundUnaries = false;
      i++;
    } else if (args[i].equalsIgnoreCase("-featureFactory")) {
      featureFactoryClass = args[i + 1];
      i += 2;
    } else if (args[i].equalsIgnoreCase("-averagedModels")) {
      averagedModels = Integer.valueOf(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-cvAveragedModels")) {
      cvAveragedModels = true;
      i++;
    } else if (args[i].equalsIgnoreCase("-noCVAveragedModels")) {
      cvAveragedModels = false;
      i++;
    } else if (args[i].equalsIgnoreCase("-recordBinarized")) {
      recordBinarized = args[i + 1];
      i += 2;
    } else if (args[i].equalsIgnoreCase("-recordDebinarized")) {
      recordDebinarized = args[i + 1];
      i += 2;
    } else if (args[i].equalsIgnoreCase("-trainingMethod")) {
      trainingMethod = TrainingMethod.valueOf(args[i + 1].toUpperCase());
      i += 2;
    }
    return i;
  }

  private static final long serialVersionUID = 1L;
}
