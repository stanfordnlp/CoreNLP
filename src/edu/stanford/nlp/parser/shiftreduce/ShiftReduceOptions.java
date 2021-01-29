package edu.stanford.nlp.parser.shiftreduce;

import java.util.Locale;

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

  @Override
  protected int setOptionFlag(String[] args, int i) {
    int j = super.setOptionFlag(args, i);
    if (i != j) {
      return j;
    }
    if (args[i].equalsIgnoreCase("-beamSize")) {
      testOptions().beamSize = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-trainBeamSize")) {
      trainOptions().beamSize = Integer.parseInt(args[i + 1]);
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
      trainOptions().averagedModels = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-cvAveragedModels")) {
      trainOptions().cvAveragedModels = true;
      i++;
    } else if (args[i].equalsIgnoreCase("-noCVAveragedModels")) {
      trainOptions().cvAveragedModels = false;
      i++;
    } else if (args[i].equalsIgnoreCase("-retrainAfterCutoff")) {
      trainOptions().retrainAfterCutoff = true;
      i++;
    } else if (args[i].equalsIgnoreCase("-noRetrainAfterCutoff")) {
      trainOptions().retrainAfterCutoff = false;
      i++;
    } else if (args[i].equalsIgnoreCase("-trainingMethod")) {
      trainOptions().trainingMethod = ShiftReduceTrainOptions.TrainingMethod.valueOf(args[i + 1].toUpperCase(Locale.ROOT));
      if (trainOptions().trainingMethod == ShiftReduceTrainOptions.TrainingMethod.BEAM ||
          trainOptions().trainingMethod == ShiftReduceTrainOptions.TrainingMethod.REORDER_BEAM) {
        if (trainOptions().beamSize <= 0) {
          trainOptions().beamSize = ShiftReduceTrainOptions.DEFAULT_BEAM_SIZE;
        }
        if (testOptions().beamSize <= 0) {
          testOptions().beamSize = trainOptions().beamSize;
        }
      }
      i += 2;
    } else if (args[i].equalsIgnoreCase("-featureFrequencyCutoff")) {
      trainOptions().featureFrequencyCutoff = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-saveIntermediateModels")) {
      trainOptions().saveIntermediateModels = true;
      i++;
    } else if (args[i].equalsIgnoreCase("-nosaveIntermediateModels")) {
      trainOptions().saveIntermediateModels = false;
      i++;
    } else if (args[i].equalsIgnoreCase("-oracleShiftToBinary")) {
      trainOptions().oracleShiftToBinary = true;
      i++;
    } else if (args[i].equalsIgnoreCase("-oracleBinaryToShift")) {
      trainOptions().oracleBinaryToShift = true;
      i++;
    } else if (args[i].equalsIgnoreCase("-recordBinarized")) {
      testOptions().recordBinarized = args[i + 1];
      i += 2;
    } else if (args[i].equalsIgnoreCase("-recordDebinarized")) {
      testOptions().recordDebinarized = args[i + 1];
      i += 2;
    } else if (args[i].equalsIgnoreCase("-recordTransitionTypes")) {
      testOptions().recordTransitionTypes = true;
      i++;
    } else if (args[i].equalsIgnoreCase("-decayLearningRate")) {
      trainOptions().decayLearningRate = Double.parseDouble(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-l1Reg")) {
      trainOptions().l1Reg = Float.parseFloat(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-l2Reg")) {
      trainOptions().l2Reg = Float.parseFloat(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-retrainShards")) {
      trainOptions().retrainShards = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-retrainShardFeatureDrop")) {
      trainOptions().retrainShardFeatureDrop = Double.parseDouble(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-augmentSubsentences")) {
      trainOptions().augmentSubsentences = Float.parseFloat(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-learnExtraTransitions")) {
      trainOptions().learnExtraTransitions = true;
      i++;
    }
    return i;
  }

  private static final long serialVersionUID = 1L;
}
