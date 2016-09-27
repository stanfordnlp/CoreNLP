package edu.stanford.nlp.coref.sieve;

public class DiscourseMatch extends DeterministicCorefSieve {
  public DiscourseMatch() {
    super();
    flags.USE_DISCOURSEMATCH = true;
  }
}
