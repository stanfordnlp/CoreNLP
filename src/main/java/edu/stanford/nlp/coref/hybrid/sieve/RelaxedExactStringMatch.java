package edu.stanford.nlp.coref.hybrid.sieve;

public class RelaxedExactStringMatch extends DeterministicCorefSieve {
  public RelaxedExactStringMatch() {
    super();
    flags.USE_RELAXED_EXACTSTRINGMATCH = true;
  }
}
