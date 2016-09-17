package edu.stanford.nlp.coref.hybrid.sieve;

public class ExactStringMatch extends DeterministicCorefSieve {
  public ExactStringMatch() {
    super();
    flags.USE_EXACTSTRINGMATCH = true;
  }
}
