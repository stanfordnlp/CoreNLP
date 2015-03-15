package edu.stanford.nlp.sentiment;

import java.io.Serializable;

/**
 * Evaluation-only options for the RNN models
 *
 * @author John Bauer
 */
public class RNNTestOptions implements Serializable {
  public int ngramRecordSize = 0;

  public int ngramRecordMaximumLength = 0;

  public boolean printLengthAccuracies = false;

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    result.append("TEST OPTIONS\n");
    result.append("ngramRecordSize=" + ngramRecordSize + "\n");
    result.append("ngramRecordMaximumLength=" + ngramRecordMaximumLength + "\n");
    result.append("printLengthAccuracies=" + printLengthAccuracies + "\n");
    return result.toString();
  }

  public int setOption(String[] args, int argIndex) {
    if (args[argIndex].equalsIgnoreCase("-ngramRecordSize")) {
      ngramRecordSize = Integer.parseInt(args[argIndex + 1]);
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-ngramRecordMaximumLength")) {
      ngramRecordMaximumLength = Integer.parseInt(args[argIndex + 1]);
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-printLengthAccuracies")) {
      printLengthAccuracies = true;
      return argIndex + 1;
    } else if (args[argIndex].equalsIgnoreCase("-noprintLengthAccuracies")) {
      printLengthAccuracies = false;
      return argIndex + 1;
    }
    return argIndex;
  }
}

