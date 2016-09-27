package edu.stanford.nlp.coref.sieve;

public class ExactStringMatch extends DeterministicCorefSieve {
  public ExactStringMatch() {
    super();
    flags.USE_EXACTSTRINGMATCH = true;
  }
}
