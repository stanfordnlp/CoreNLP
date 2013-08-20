/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 * @author Kristina Toutanova
 * @version 1.0
 */


package edu.stanford.nlp.tagger.maxent;


class ExtractorLastVerbal extends DictionaryExtractor {

  private static final long serialVersionUID = -2883166900158556276L;

  private static final int commPart = 1;
  private static final int commtakesPart = 1;
  private static final String partTag = "RP";
  private static final String inTag = "IN";

  public ExtractorLastVerbal() {
  }

  @Override
  public boolean precondition(String tag) {
    return (tag.equals(partTag) || (tag.equals(inTag)));
  }


  @Override
  String extract(History h, PairsHolder pH) {
    // should extract last verbal word and also the current word
    String cword = pH.getWord(h, 0);
    if (dict.getCount(cword, partTag) < commPart) {
      return "0";
    }

    String lastverb = extractLV(h, pH);
    if (lastverb.startsWith("NA")) {
      return "0";
    }

    if (isPartikleTakingVerb(lastverb)) {
      //System.out.println(" part taking");
      return ("1");
    }
    return "2";
  }


  @Override
  String extract(History h, PairsHolder pH, int bound) {
    // should extract last verbal word and also the current word
    String cword = pH.getWord(h, 0);
    if (dict.getCount(cword, partTag) < commPart) {
      return "0";
    }
    int start = h.start;
    String lastverb = "NA";
    int current = h.current;
    int index = current - 1;
    while (index >= start) {
      String tag = pH.getTag(index);
      if (tag.startsWith("VB")) {
        lastverb = pH.getWord(index);
        break;
      }
      index--;
    }
    if (lastverb.startsWith("NA")) {
      return "0";
    }

    if (isPartikleTakingVerb(lastverb, bound)) {
      //System.out.println(" part taking");
      return ("1");
    }
    return ("2");


  }


  private boolean isPartikleTakingVerb(String verb) {
    //System.out.println(verb);
    return (dict.getCountPart(verb) > commtakesPart);
  }


  private boolean isPartikleTakingVerb(String verb, int bound) {
    //System.out.println(verb);
    return dict.getCountPart(verb) > bound;
  }


}



