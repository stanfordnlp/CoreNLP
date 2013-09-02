package edu.stanford.nlp.parser.lexparser;

import java.util.Map;


public class GermanUnknownWordModelTrainer
  extends BaseUnknownWordModelTrainer {

  @Override
  protected UnknownWordModel buildUWM() {
    Map<String,Float> unknownGT = null;
    if (useGT) {
      unknownGT = unknownGTTrainer.unknownGT;
    }
    return new GermanUnknownWordModel(op, lex, wordIndex, tagIndex,
                                      unSeenCounter, tagHash,
                                      unknownGT, seenEnd);
  }

}