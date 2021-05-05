package edu.stanford.nlp.parser.shiftreduce;

import edu.stanford.nlp.parser.lexparser.TestOptions;

public class ShiftReduceTestOptions extends TestOptions {
  public String recordBinarized = null;

  public String recordDebinarized = null;

  public int beamSize = 0;

  public boolean recordTransitionTypes;
}
