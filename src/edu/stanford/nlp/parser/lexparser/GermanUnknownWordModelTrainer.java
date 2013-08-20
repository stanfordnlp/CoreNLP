package edu.stanford.nlp.parser.lexparser;

import java.util.HashMap;
import edu.stanford.nlp.ling.Label;

public class GermanUnknownWordModelTrainer
  extends BaseUnknownWordModelTrainer 
{
  protected UnknownWordModel buildUWM() {
    HashMap<String,Float> unknownGT = null;
    if (useGT) {
      unknownGT = unknownGTTrainer.unknownGT;
    }
    return new GermanUnknownWordModel(op, lex, wordIndex, tagIndex, 
                                      unSeenCounter, tagHash, 
                                      unknownGT, seenEnd);
  }
}