/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 */
package edu.stanford.nlp.tagger.maxent;

import java.util.HashMap;
import java.util.HashSet;


/**
 * A class that detects and provides features for common verb particle pairs.
 *
 * @author Kristina Toutanova
 * @version 1.0
 */
public class ExtractorParticlesChris extends DictionaryExtractor {

  private static final String partTag = "RP";
  private static final String inTag = "IN";
  private static final String rbTag = "RB";
  private final int bound;   // usually 3 but 2 for local inference
  private HashSet<String> s = new HashSet<String>();
  private HashMap<String,String> forms = new HashMap<String,String>();

  @Override
  public boolean precondition(String tag) {
    return (tag.equals(partTag) || (tag.equals(inTag)) || (tag.equals(rbTag)));
  }


  public ExtractorParticlesChris() {
    this(3);
  }


  public ExtractorParticlesChris(int bound) {
    this.bound = bound;
    s.add("back:down");
    s.add("back:up");
    s.add("backed:down");
    s.add("backed:up");
    s.add("bail:out");
    s.add("blip:up");
    s.add("beef:up");
    s.add("bottle:up");
    s.add("bottom:out");
    s.add("break:down");
    s.add("break:out");
    s.add("break:up");
    s.add("bring:out");
    s.add("build:up");
    s.add("carry:out");
    s.add("carried:out");
    s.add("catch:up");
    s.add("caught:up");
    s.add("clean:up");
    s.add("cover:up");
    s.add("cut:off");
    s.add("draw:up");
    s.add("drew:up");
    s.add("drawn:up");
    s.add("drive:down");
    s.add("drove:down");
    s.add("driven:down");
    s.add("fend:off");
    s.add("fight:off");
    s.add("fought:off");
    s.add("figure:out");
    s.add("figured:out");
    s.add("give:up");
    s.add("gave:up");
    s.add("hold:up");
    s.add("held:up");
    s.add("keep:up");
    s.add("kept:up");
    s.add("knock:down");
    s.add("make:up");
    s.add("made:up");
    s.add("open:up");
    s.add("pay:off");
    s.add("pick:up");
    s.add("prop:up");
    s.add("put:up");
    s.add("rule:out");
    s.add("ruled:out");
    s.add("seek:out");
    s.add("sought:out");
    s.add("sell:off");
    s.add("sell:out");
    s.add("set:off");
    s.add("set:out");
    s.add("set:up");
    s.add("slow:down");
    s.add("snap:up");
    s.add("spin:off");
    s.add("tie:up");
    s.add("tied:up");
    s.add("touch:off");
    s.add("turn:off");
    s.add("turn:out");
    s.add("wipe:out");
    s.add("wiped:up");
    s.add("work:out");
    s.add("work:up");
    s.add("write:off");
    s.add("wrote:off");
    s.add("written:off");
    forms.put("drew", "draw");
    forms.put("drawn", "draw");
    forms.put("drove", "drive");
    forms.put("driven", "drive");
    forms.put("fought", "fight");
    forms.put("figured", "figure");
    forms.put("gave", "give");
    forms.put("given", "give");
    forms.put("held", "hold");
    forms.put("kept", "keep");
    forms.put("made", "make");
    forms.put("paid", "pay");
    forms.put("bottled", "bottle");
    forms.put("broke", "break");
    forms.put("broken", "break");
    forms.put("brought", "bring");
    forms.put("built", "build");
    forms.put("carried", "carry");
    forms.put("caught", "catch");
    forms.put("ruled", "rule");
    forms.put("sought", "seek");
    forms.put("tied", "tie");
    forms.put("wiped", "wipe");
    forms.put("wrote", "write");
    forms.put("written", "write");
    forms.put("sold", "sell");
  }


  String getMainForm(String verb1) {
    String verb = TestSentence.toNice(verb1);
    if (forms.containsKey(verb)) {
      return forms.get(verb);
    }
    if (verb.endsWith("ed") || verb.endsWith("en")) {
      return verb.substring(0, verb.length() - 2);
    }
    return verb;
  }


  String extract(History h, PairsHolder pH) {
    // should extract last verbal word and also the current word
    String cword = pH.getWord(h, 0);
    if (dict.getCount(cword, partTag) < 1) {
      return "0";
    }

    String lastverb = extractLV(h, pH, bound);
    if (lastverb.startsWith("NA")) {
      return "3";
    }
    lastverb = getMainForm(lastverb);
    //System.out.println(lastverb+" got lastverb ");
    cword = TestSentence.toNice(cword);
    if (isPartikleTakingVerb(lastverb, cword)) {
      // TODO: restore the debugging output & the VERBOSE option
      //System.out.println(" part taking");
      //if (globalHolder.VERBOSE) {
      //  System.out.println(" part taking " + lastverb + ' ' + cword);
      //  System.out.println(cword + "is particle");
      //}
      return "1";
    }
    return "2";
  }



  public boolean isPartikleTakingVerb(String lastverb, String cWord) {
    return s.contains(lastverb + ':' + cWord);
  }

  private static final long serialVersionUID = 2631506606864457451L;

}
