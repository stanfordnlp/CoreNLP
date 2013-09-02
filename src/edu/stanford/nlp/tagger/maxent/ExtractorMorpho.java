/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 * @author Kristina Toutanova
 * @version 1.0
 */


package edu.stanford.nlp.tagger.maxent;

import edu.stanford.nlp.util.ErasureUtils;

public class ExtractorMorpho extends DictionaryExtractor {

  private static final long serialVersionUID = -2160678158830966722L;

  int num;   // num is from 1 to 5 , 5 means the dash separation
  int t1;
  int t2 = -1;
  String tag1;
  String tag2;


  // public boolean isPopulated(BinaryFeature f) {
  //   return (f.indexedValues.length > GlobalHolder.rareWordMinFeatureThresh);
  // }

  public ExtractorMorpho(int num, int t1, TTags ttags) {
    this.num = num;
    this.t1 = t1;
    tag1 = ttags.getTag(t1);
  }


  public ExtractorMorpho(int num, int t1, int t2, TTags ttags) {
    ErasureUtils.noop(num);
    this.t1 = t1;
    this.t2 = t2;
    tag1 = ttags.getTag(t1);
    tag2 = ttags.getTag(t2);
  }


  @Override
  String extract(History h, PairsHolder pH) {
    String word = TestSentence.toNice(pH.getWord(h, 0));
    if (t2 == -1) {
      if (word.length() < num) {
        return "0";
      }
      String wpref = word.substring(0, word.length() - num);
      if (dict.getCount(wpref, tag1) > 0) {
        return word.substring(word.length() - num, word.length()) + tag1;
      } else {
        return "0";
      }
    }  // if t2 is -1
    int dashIndex = word.indexOf('-');
    if (dashIndex > -1) {
      String wordpref = word.substring(0, dashIndex);
      String wordsuff = word.substring(dashIndex + 1);
      if ((dict.getCount(wordpref, tag1) > 0) && (dict.getCount(wordsuff, tag2) > 0)) {
        return tag1 + "&" + tag2;
      }
    } // dashIndex
    return "0";


  }

}
