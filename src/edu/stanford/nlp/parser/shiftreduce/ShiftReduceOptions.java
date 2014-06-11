package edu.stanford.nlp.parser.shiftreduce;

import edu.stanford.nlp.parser.lexparser.Options;

public class ShiftReduceOptions extends Options {
  public int beamSize = 1;

  public boolean compoundUnaries = true;

  public String featureFactoryClass = "edu.stanford.nlp.parser.shiftreduce.BasicFeatureFactory";

  protected int setOptionFlag(String[] args, int i) {
    int j = super.setOptionFlag(args, i);
    if (i != j) {
      return j;
    }
    if (args[i].equalsIgnoreCase("-beamSize")) {
      beamSize = Integer.valueOf(args[i + 1]);
      i = i + 2;
    } else if (args[i].equalsIgnoreCase("-compoundUnaries")) {
      compoundUnaries = true;
      i++;
    } else if (args[i].equalsIgnoreCase("-nocompoundUnaries")) {
      compoundUnaries = false;
      i++;
    } else if (args[i].equalsIgnoreCase("-featureFactory")) {
      featureFactoryClass = args[i + 1];
      i += 2;
    }
    return i;
  }

  private static final long serialVersionUID = 1L;
}
