package edu.stanford.nlp.hcoref.sieve;

public class SpeakerMatch extends DeterministicCorefSieve {
  public SpeakerMatch() {
    super();
    flags.USE_SPEAKERMATCH = true;
  }
}
