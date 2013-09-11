/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 * @author Kristina Toutanova
 * @version 1.0
 */
package edu.stanford.nlp.tagger.maxent;

public class ExtractorFollowingWClass extends DictionaryExtractor {

  private static final long serialVersionUID = -1718985062915165188L;

  private final Extractor nextXWord;

  @Override
  String extract(History h, PairsHolder pH) {
    // should extract last verbal word and also the current word
    String nWord = nextXWord.extract(h, pH);
    int classId = dict.getAmbClass(nWord);
    return Integer.toString(classId);
  }

  protected ExtractorFollowingWClass(int num) {
    nextXWord = new Extractor(num, false);
  }

  @Override public boolean isLocal() { return false; }
  @Override public boolean isDynamic() { return false; }
}
