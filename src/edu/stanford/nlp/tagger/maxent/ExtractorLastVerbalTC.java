/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 * @author Kristina Toutanova
 * @version 1.0
 */
package edu.stanford.nlp.tagger.maxent;

/**
 * Extractor whether the last verbal takes a that complement.
 */
class ExtractorLastVerbalTC extends DictionaryExtractor {
  private static final int commThat = 0;
  private static final String thatWord = "that";
  //private static final String inTag = "IN";
  //private static final String wdtTag = "WDT";
  //private static final String dtTag = "DT";

  public ExtractorLastVerbalTC() {
  }

  @Override
  String extract(History h, PairsHolder pH) {
    // should extract last verbal word and also the current word
    String cword = pH.getWord(h, 0);
    if (!cword.equals(thatWord)) {
      return "0";
    }
    //String ctag = pH.getTag(h, 0);
    int start = h.start;
    String lastverb = "NA";
    int current = h.current;
    int index = current - 1;
    String tag; //= "NA";
    while (index >= start) {
      tag = pH.getTag(index);
      if (tag.startsWith("VB")) {
        lastverb = pH.getWord(index);
        break;
      }
      index--;
    }
    if (lastverb.startsWith("NA")) {
      return "3";
    }

    if (isThatComplTakingVerb(lastverb)) {
      //System.out.println(" part taking");
      return ("1");
    }
    return ("2");
  }


  @Override
  String extract(History h, PairsHolder pH, int bound) {
    // should extract last verbal word and also the current word
    String cword = pH.getWord(h, 0);
    if (!cword.equals(thatWord)) {
      return "0";
    }
    String lastverb = extractLV(h, pH);
    if (lastverb.startsWith("NA")) {
      return "0";
    }

    if (isThatComplTakingVerb(lastverb, bound)) {
      //System.out.println(" part taking");
      return ("1");
    }
    return ("2");
  }


  private boolean isThatComplTakingVerb(String verb) {
    //System.out.println(verb);
    if (dict.isUnknown(verb)) {
      int i = (int) (Math.random() * 2);
      if (i == 0) {
        // pretend the verb takes that complements
        dict.addVThatTaking(verb);
        return true;
      }
    }
    return (dict.getCountThat(verb) > commThat);
  }


  private boolean isThatComplTakingVerb(String verb, int bound) {
    //System.out.println(verb);
    return dict.getCountThat(verb) > bound;
  }

  private static final long serialVersionUID = -2324821292516788746L;

}
