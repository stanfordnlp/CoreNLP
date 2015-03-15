package edu.stanford.nlp.hcoref.sieve;

public class DiscourseMatch extends DeterministicCorefSieve {
  public DiscourseMatch() {
    super();
    flags.USE_DISCOURSEMATCH = true;
  }
}
