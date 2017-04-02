package edu.stanford.nlp.coref.sieve;

public class SpeakerMatch extends DeterministicCorefSieve {
  public SpeakerMatch() {
    super();
    flags.USE_SPEAKERMATCH = true;
  }
}
