/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 */


package edu.stanford.nlp.tagger.maxent;

/**
 * @author Kristina Toutanova
 * @version 1.0
 */
public class ExtractorLVdist extends Extractor {

  private static final long serialVersionUID = 3941584365171145958L;

  private static final int bound = 5;

  @Override
  String extract(History h, PairsHolder pH) {
    // should extract last verbal word and also the current word
    // String cword = pH.getWord(h, 0);
    return extractLV(h, pH, bound);
  }

  /** This one puts the distance into the feature name. */
  @Override
  String extractLV(History h, PairsHolder pH, int bound) {
    // should extract last verbal word and also the current word
    int start = h.start;
    String lastverb = "NA";
    int current = h.current;
    int index = current - 1;
    while ((index >= start) && (index >= current - bound)) {
      String tag = h.pairs.getTag(index);
      if (tag.startsWith("VB")) {
        lastverb = h.pairs.getWord(index) + '#' + (current - index);
        break;
      }
      if (tag.startsWith(",")) {
        break;
      }
      index--;
    }
    return lastverb;

  }

  public ExtractorLVdist() {
  }

}
