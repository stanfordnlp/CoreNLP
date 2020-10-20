package edu.stanford.nlp.coref.hybrid.sieve;

public class LexicalChainMatch extends DeterministicCorefSieve {

  public LexicalChainMatch() {
    super();

    flags.USE_iwithini = true;
    flags.USE_ATTRIBUTES_AGREE = true;
    flags.USE_WN_HYPERNYM = true;
    flags.USE_WN_SYNONYM = true;
    flags.USE_DIFFERENT_LOCATION = true;
    flags.USE_NUMBER_IN_MENTION = true;
  }
}
