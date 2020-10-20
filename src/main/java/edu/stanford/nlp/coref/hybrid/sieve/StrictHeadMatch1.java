package edu.stanford.nlp.coref.hybrid.sieve;

public class StrictHeadMatch1 extends DeterministicCorefSieve {
  public StrictHeadMatch1() {
    super();
    flags.USE_iwithini = true;
    flags.USE_INCLUSION_HEADMATCH = true;
    flags.USE_INCOMPATIBLE_MODIFIER = true;
    flags.USE_WORDS_INCLUSION = true;
  }
}
