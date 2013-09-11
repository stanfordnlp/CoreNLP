
/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 */


package edu.stanford.nlp.tagger.maxent;

/**
 * This marks cases of the word "that" followed by a known plural noun.
 * Currently unused.
 * 
 * @author Kristina Toutanova
 * @version 1.0
 */
class ExtractorFollowingNN extends DictionaryExtractor {

  private static final long serialVersionUID = -4668571115564345146L;

  private static final String thatWord = "that";
  private static final String nnTag = "NN";
  private static final String nnsTag = "NNS";
  // private static final String nnpTag = "NNP";
  // private static final String nnpsTag = "NNPS";

  public ExtractorFollowingNN() {
  }

  @Override
  String extract(History h, PairsHolder pH) {
    // should extract last verbal word and also the current word
    String cword = pH.getWord(h, 0);
    String nWord = pH.getWord(h, 1);
    if (!cword.equals(thatWord)) {
      return "0";
    }

    if (nWord.startsWith("NA")) {
      return "0";
    }

    if (isNSCount(nWord)) {
      //System.out.println(" part taking");
      return ("1");
    }
    return ("0");
  }

  private boolean isNSCount(String word) {
    //System.out.println(verb);
    // if (dict.getCount(word,nnsTag)>0) return false;
    // if (dict.getCount(word,nnpTag)>0) return false;
    //if (dict.getCount(word,nnpsTag)>0) return false;
    if (dict.getCount(word, nnTag) > 0) {
      String word1 = TestSentence.toNice(word) + "s";
      if (dict.getCount(word1, nnsTag) > 0) {
        return true;
      }
    }
    return false;
  }

}


