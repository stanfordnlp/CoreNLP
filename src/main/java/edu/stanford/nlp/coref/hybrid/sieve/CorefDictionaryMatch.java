package edu.stanford.nlp.coref.hybrid.sieve;

/**
 *  Sieve that uses the coreference dictionary for the technical domain
 *  developed by Recasens, Can and Jurafsky (NAACL 2013).
 *
 *  @author recasens
 */
public class CorefDictionaryMatch extends DeterministicCorefSieve {
  public CorefDictionaryMatch(){
    super();
    flags.USE_iwithini = true;
    flags.USE_DIFFERENT_LOCATION = true;
    flags.USE_NUMBER_IN_MENTION = true;
    flags.USE_DISTANCE = true;
    flags.USE_ATTRIBUTES_AGREE = true;
    flags.USE_COREF_DICT = true;
  }
}
