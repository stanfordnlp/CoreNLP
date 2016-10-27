package edu.stanford.nlp.dcoref.sievepasses;

public class StrictHeadMatch2 extends DeterministicCorefSieve {
  public StrictHeadMatch2() {
    super();
    flags.USE_iwithini = true;
    flags.USE_INCLUSION_HEADMATCH = true;
    flags.USE_WORDS_INCLUSION = true;
  }
}
