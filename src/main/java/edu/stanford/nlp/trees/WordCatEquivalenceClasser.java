package edu.stanford.nlp.trees;

import edu.stanford.nlp.stats.EquivalenceClasser;

/**
 * An EquivalenceClasser for WordCatConstituents.  WCCs are equivalent iff
 * they are of the same type (word, cat, tag).
 *
 * @author Galen Andrew
 */
public class WordCatEquivalenceClasser implements EquivalenceClasser {

  public Object equivalenceClass(Object o) {
    WordCatConstituent lb = (WordCatConstituent) o;
    return lb.type;
  }
}
