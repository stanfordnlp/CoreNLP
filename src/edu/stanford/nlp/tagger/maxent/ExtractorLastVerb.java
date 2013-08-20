/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 * @author Kristina Toutanova
 * @version 1.0
 */
package edu.stanford.nlp.tagger.maxent;

public class ExtractorLastVerb extends DictionaryExtractor {

  private static final String partTag = "RP";
  private static final String inTag = "IN";
  private static final String rbTag = "RB";
  private static final String rareVerb = "rareverbed";
  private final int bound;

  public ExtractorLastVerb() {
    this(8);
  }

  public ExtractorLastVerb(int bound) {
    this.bound = bound;
  }


  @Override
  public boolean precondition(String tag) {
    return (tag.equals(partTag) || (tag.equals(inTag)) || (tag.equals(rbTag)));
  }


  @Override
  String extract(History h, PairsHolder pH) {
    // should extract last verbal word and also the current word
    String cword = pH.getWord(h, 0);
    int allCount = dict.sum(cword);
    int rpCount = dict.getCount(cword, "RP");
    int rbCount = dict.getCount(cword, "RB");
    int inCount = dict.getCount(cword, "IN");
    if ((allCount == 0) || ((allCount > 0) && (rpCount + rbCount + inCount <= allCount / 100))) {
      return zeroSt;
    }
    String lastverb = extractLV(h, pH, bound);
    if (lastverb.startsWith("NA")) {
      return "2";
    }
    if (dict.sum(lastverb) < 2) {
      return rareVerb + '|' + cword;
    }
    return lastverb + '|' + cword;
  }

  private static final long serialVersionUID = -16619310062224383L;

}
