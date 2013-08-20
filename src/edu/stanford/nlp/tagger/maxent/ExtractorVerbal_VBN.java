/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 * @author Kristina Toutanova
 * @version 1.0
 */

package edu.stanford.nlp.tagger.maxent;

import edu.stanford.nlp.stats.ClassicCounter;


/**
 * Look for features selecting a VBN verb.
 */
class ExtractorVerbal_VBN extends DictionaryExtractor {

  private static final String vbnTag = "VBN";
  private static final String vbdTag = "VBD";
  private static final String jjTag = "JJ";
  private static final String edSuff = "ed";
  private static final String enSuff = "en";
  private static final String oneSt = "1";
  private static final String naWord = "NA";
  private static final String bar = "|";
  private static int total = 0;
  private static int notUsual = 0;
  private static int unRec = 0;
  private ClassicCounter<String> counter = new ClassicCounter<String>();
  private final int bound;

  public ExtractorVerbal_VBN() {
    this(8);
  }

  public ExtractorVerbal_VBN(int bound) {
    this.bound = bound;
  }


  @Override
  public boolean precondition(String tag) {
    return (tag.equals(vbnTag) || (tag.equals(vbdTag)) || (tag.equals(jjTag)));
  }


  public void getTrigger(History h, String tag1) {
    if (!tag1.equals(vbnTag)) {
      return;
    }
    String lastvtag = "";
    String lastverb = naWord;
    int current = h.current;
    int start = h.start;
    int index = current - 1;
    while ((index >= start) && (index >= current - bound)) {
      String tag = h.pairs.getTag(index);
      // String word = GlobalHolder.pairs.get(index, false);
      if (tag.startsWith("VB")) {
        lastverb = h.pairs.getWord(index);
        lastvtag = tag;
        break;
      }
      index--;
    }

    if (isForVBN(lastverb)) {
      total++;
      // h.printSent();
      System.out.println(lastverb + bar + lastvtag);
      return;
    }
    h.printSent();
    notUsual++;

    if (lastvtag.length() == 0) {
      //System.out.println(" unrecognized cause ");
      unRec++;
      total++;
    } else {
      //System.out.println(lastverb+bar+lastvtag);
      total++;
      counter.incrementCount(lastverb + bar + lastvtag);
    } // else
  }


  public void print() {
    System.out.println("total " + total + " unrec " + unRec + " not usual " + notUsual);
    System.out.println(counter);
  }


  @Override
  String extract(History h, PairsHolder pH) {
    // should extract last verbal word and also the current word
    String cword = pH.getWord(h, 0);
    int start = h.start;
    int allCount = dict.sum(cword);
    int vBNCount = dict.getCount(cword, vbnTag);
    int vBDCount = dict.getCount(cword, vbdTag);
    String cwordnice = TestSentence.toNice(cword);

    if ((allCount == 0) && (!(cwordnice.endsWith(edSuff) || cwordnice.endsWith(enSuff)))) {
      return zeroSt;
    }
    if ((allCount > 0) && (vBNCount + vBDCount <= allCount / 100)) {
      return zeroSt;
    }
    String lastverb = naWord;
    //String lastvtag = zeroSt; // mg: written but never read
    int current = h.current;
    int index = current - 1;
    while ((index >= start) && (index >= current - 8)) {
      String tag = pH.getTag(index);
      if ((tag.startsWith("VB"))) {
        lastverb = pH.getWord(index);
        //lastvtag = tag;
        break;
      }
      if (tag.startsWith(",")) {
        //lastvtag = tag;
        break;
      }
      index--;
    }


    if (lastverb.equals(naWord)) {
      return zeroSt;
    }

    if (isForVBN(lastverb)) {
      //System.out.println(" is for vbn ");
      return oneSt;
    }
    return zeroSt;
  }

  private boolean isForVBN(String verb) {
    String verbnice = TestSentence.toNice(verb);
    //System.out.println(verb);
    if (dict.isUnknown(verb)) {
      return false;
    }
    if (verbnice.equals("have")) {
      return true;
    }
    if (verbnice.equals("has")) {
      return true;
    }
    if (verbnice.equals("having")) {
      return true;
    }
    if (verbnice.equals("had")) {
      return true;
    }
    if (verbnice.equals("is")) {
      return true;
    }
    if (verbnice.equals("am")) {
      return true;
    }
    if (verbnice.equals("are")) {
      return true;
    }
    if (verbnice.equals("was")) {
      return true;
    }
    if (verbnice.equals("were")) {
      return true;
    }
    if (verbnice.equals("be")) {
      return true;
    }
    if (verbnice.equals("been")) {
      return true;
    }
    if (verbnice.equals("'ve")) {
      return true;
    }
    if (verbnice.equals("'s")) {
      return true;
    }
    if (verbnice.equals("'re")) {
      return true;
    }
    if (verbnice.equals("'m")) {
      return true;
    }
    if (verbnice.equals("being")) {
      return true;
    }
    if (verbnice.equals("got")) {
      return true;
    }
    if (verbnice.equals("gets")) {
      return true;
    }
    if (verbnice.equals("get")) {
      return true;
    }
    if (verbnice.equals("getting")) {
      return true;
    }
    // if(verbnice.equals("keeping")) return true;
    return false;
  }

  private static final long serialVersionUID = -5881204185400060636L;

}
