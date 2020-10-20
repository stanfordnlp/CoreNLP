package edu.stanford.nlp.trees;

import edu.stanford.nlp.stats.EquivalenceClassEval;

/**
 * An EqualityChecker for WordCatConstituents.  
 * Words only have to have the correct span
 * while tags (word/POS) and cats (labeled brackets) must have correct span
 * and label.
 *
 * @author Galen Andrew
 */
public class WordCatEqualityChecker implements EquivalenceClassEval.EqualityChecker {

  public boolean areEqual(Object o, Object o2) {
    WordCatConstituent span = (WordCatConstituent) o;
    WordCatConstituent span2 = (WordCatConstituent) o2;
    if (span.type != span2.type) {
      return false;
    } else if (span.start() != span2.start() || span.end() != span2.end()) {
      return false;
    } else if (span.type != WordCatConstituent.wordType && !span.value().equals(span2.value())) {
      return false;
    } else {
      return true;
    }
  }
}
