package edu.stanford.nlp.coref.sieve;

public class PronounMatch extends DeterministicCorefSieve {
  public PronounMatch() {
    super();
    flags.USE_iwithini = true;
    flags.DO_PRONOUN = true;
  }
}
