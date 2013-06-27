package edu.stanford.nlp.dcoref.sievepasses;

public class AliasMatch extends DeterministicCorefSieve {
  public AliasMatch() {
    super();
    flags.USE_iwithini = true;
    flags.USE_ATTRIBUTES_AGREE = true;
    flags.USE_ALIAS = true;
    flags.USE_DIFFERENT_LOCATION = true;
    flags.USE_NUMBER_IN_MENTION = true;
  }
}
