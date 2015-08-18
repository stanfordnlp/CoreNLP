package edu.stanford.nlp.hcoref.sieve;

public class RelaxedExactStringMatch extends DeterministicCorefSieve {
  public RelaxedExactStringMatch() {
    super();
    flags.USE_RELAXED_EXACTSTRINGMATCH = true;
  }
}
