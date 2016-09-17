package edu.stanford.nlp.hcoref.sieve;

public class MarkRole extends DeterministicCorefSieve {
  public MarkRole() {
    super();
    flags.USE_ROLE_SKIP = true;
  }
}
