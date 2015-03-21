package edu.stanford.nlp.hcoref.sieve;

public class ExactStringMatch extends DeterministicCorefSieve {
  public ExactStringMatch() {
    super();
    flags.USE_EXACTSTRINGMATCH = true;
  }
}
