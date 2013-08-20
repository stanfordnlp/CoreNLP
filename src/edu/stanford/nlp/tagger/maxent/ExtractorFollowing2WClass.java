/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 * @author Kristina Toutanova
 * @version 1.0
 */

package edu.stanford.nlp.tagger.maxent;


public class ExtractorFollowing2WClass extends DictionaryExtractor {

  private static final long serialVersionUID = 208892521920629670L;

  private final static Extractor nextWord = new Extractor(1, false);
  private final static Extractor next2Word = new Extractor(2, false);

  @Override
  String extract(History h, PairsHolder pH) {
    // should extract last verbal word and also the current word

    String nWord = nextWord.extract(h, pH);
    String n2Word = next2Word.extract(h, pH);

    int classId = dict.getAmbClass(nWord);
    int class2Id = dict.getAmbClass(n2Word);
    return classId + "|" + class2Id;
  }

  public ExtractorFollowing2WClass() {
  }

  @Override public boolean isLocal() { return false; }
  @Override public boolean isDynamic() { return false; }
}
