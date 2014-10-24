package edu.stanford.nlp.patterns.surface;

import edu.stanford.nlp.ling.CoreLabel;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by sonalg on 10/15/14.
 */
public class SentenceIndex {

  Set<String> stopWords, specialWordsList;

  public SentenceIndex(boolean matchLowerCaseContext, Set<String> stopWords, Set<String> specialwords4Index, boolean batchProcessSents, String prefixFileForIndex) {
  }

  public void add(Map<String, List<CoreLabel>> sentsf, String filename, boolean useLemmaContextTokens) {
  }

  public Set<String> getSpecialWordsList() {
    return specialWordsList;
  }
}
