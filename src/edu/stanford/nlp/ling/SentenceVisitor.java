package edu.stanford.nlp.ling;

import java.util.ArrayList;

/**
 * This is a simple interface for operations that are going to be applied
 * to a <code>Sentence</code>. It typically is called iteratively over
 * sentences in a <code>Sentencebank</code>
 *
 * @author Christopher Manning
 */
public interface SentenceVisitor<T extends HasWord> {

  /**
   * Does whatever one needs to do to a particular sentence.
   *
   * @param s A sentence.  Classes implementing this interface can assume
   *          that the sentence passed in is not <code>null</code>.
   */
  public void visitSentence(ArrayList<T> s);

}

