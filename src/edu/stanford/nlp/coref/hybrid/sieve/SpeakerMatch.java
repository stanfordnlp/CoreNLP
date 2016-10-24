package edu.stanford.nlp.coref.hybrid.sieve;

public class SpeakerMatch extends DeterministicCorefSieve {
  public SpeakerMatch() {
    super();
    flags.USE_SPEAKERMATCH = true;
  }
}
