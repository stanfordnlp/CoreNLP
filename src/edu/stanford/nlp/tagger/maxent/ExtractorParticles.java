/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 * @author Kristina Toutanova
 * @version 1.0
 */

package edu.stanford.nlp.tagger.maxent;

public class ExtractorParticles extends DictionaryExtractor {

  private static final long serialVersionUID = 3098858852604017599L;

  private static final int commPart = 5;
  //private static final int commtakesPart = 1;
  private static final int bound = 3;
  private static final float cutoff = (float) 0.28;
  private static final String partTag = "RP";
  private static final String inTag = "IN";
  private static final String rbTag = "RB";

  public ExtractorParticles() {
  }

  @Override
  public boolean precondition(String tag) {
    return (tag.equals(partTag) || (tag.equals(inTag)) || (tag.equals(rbTag)));
  }


  @Override
  String extract(History h, PairsHolder pH) {
    // should extract last verbal word and also the current word
    String cword = pH.getWord(h, 0);
    if (dict.getCount(cword, partTag) < commPart) {
      return "0";
    }

    String lastverb = extractLV(h, pH, bound);
    if (lastverb.startsWith("NA")) {
      return "0";
    }

    if (isPartikleTakingVerb(lastverb)) {
      //System.out.println(" part taking");
      System.out.println(" part taking " + lastverb + " " + cword + h.pairs.getTag(h, 0));
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
    int cRP = dict.getCountPart(verb);
    int cOthers = dict.getCountIn(verb) + dict.getCountRB(verb);
    //int cRPThisWord=dict.getCountPart(verb,word);
    //int cOThersThisWord=dict.getCountIn(verb,word)+dict.getRB(verb,word);
    //if(cRPThis/(float)cOThersThisWord > cutoff) return true;

    return (cRP > 0) && (cRP / (float) cOthers > cutoff);
  }


  private boolean isPartikleTakingVerb(String verb, int bound) {
    //System.out.println(verb);
    return (dict.getCountPart(verb) > bound);
  }

}
