package edu.stanford.nlp.parser.shiftreduce;

import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.parser.lexparser.TestOptions;
import edu.stanford.nlp.parser.lexparser.TrainOptions;
import edu.stanford.nlp.util.ErasureUtils;

public class ShiftReduceOptions extends Options {
  @Override
  public TrainOptions newTrainOptions() {
    return new ShiftReduceTrainOptions();
  }

  @Override
  public TestOptions newTestOptions() {
    return new ShiftReduceTestOptions();
  }

  ShiftReduceTrainOptions trainOptions() {
    return ErasureUtils.uncheckedCast(trainOptions);
  }

  ShiftReduceTestOptions testOptions() {
    return ErasureUtils.uncheckedCast(testOptions);
  }

  public boolean compoundUnaries = true;

  public String featureFactoryClass = "edu.stanford.nlp.parser.shiftreduce.BasicFeatureFactory";

  protected int setOptionFlag(String[] args, int i) {
    int j = super.setOptionFlag(args, i);
    if (i != j) {
      return j;
    }
    if (args[i].equalsIgnoreCase("-beamSize")) {
      testOptions().beamSize = Integer.valueOf(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-trainBeamSize")) {
      trainOptions().beamSize = Integer.valueOf(args[i + 1]);
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
      trainOptions().averagedModels = Integer.valueOf(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-cvAveragedModels")) {
      trainOptions().cvAveragedModels = true;
      i++;
    } else if (args[i].equalsIgnoreCase("-noCVAveragedModels")) {
      trainOptions().cvAveragedModels = false;
      i++;
    } else if (args[i].equalsIgnoreCase("-trainingMethod")) {
      trainOptions().trainingMethod = ShiftReduceTrainOptions.TrainingMethod.valueOf(args[i + 1].toUpperCase());
      i += 2;
    } else if (args[i].equalsIgnoreCase("-featureFrequencyCutoff")) {
      trainOptions().featureFrequencyCutoff = Integer.valueOf(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-saveIntermediateModels")) {
      trainOptions().saveIntermediateModels = true;
      i++;
    } else if (args[i].equalsIgnoreCase("-nosaveIntermediateModels")) {
      trainOptions().saveIntermediateModels = false;
      i++;
    } else if (args[i].equalsIgnoreCase("-recordBinarized")) {
      testOptions().recordBinarized = args[i + 1];
      i += 2;
    } else if (args[i].equalsIgnoreCase("-recordDebinarized")) {
      testOptions().recordDebinarized = args[i + 1];
      i += 2;
    }
    return i;
  }

  private static final long serialVersionUID = 1L;
}
