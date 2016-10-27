package edu.stanford.nlp.dcoref.sievepasses;

public class StrictHeadMatch4 extends DeterministicCorefSieve {
  public StrictHeadMatch4() {
    super();
    flags.USE_iwithini = true;
    flags.USE_INCLUSION_HEADMATCH = true;
    flags.USE_PROPERHEAD_AT_LAST = true;
    flags.USE_DIFFERENT_LOCATION = true;
    flags.USE_NUMBER_IN_MENTION = true;
    flags.USE_ATTRIBUTES_AGREE = true;
  }
}
