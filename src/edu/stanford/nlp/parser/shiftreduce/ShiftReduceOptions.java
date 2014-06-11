package edu.stanford.nlp.parser.shiftreduce;

import edu.stanford.nlp.parser.lexparser.Options;

public class ShiftReduceOptions extends Options {
  public int beamSize = 1;

  protected int setOptionFlag(String[] args, int i) {
    int j = super.setOptionFlag(args, i);
    if (i != j) {
      return j;
    }
    if (args[i].equalsIgnoreCase("-beamSize")) {
      beamSize = Integer.valueOf(args[i + 1]);
      i = i + 2;
    }
    return i;
  }
}
