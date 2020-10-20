package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.Label;

/**
 * A <code>LabeledScoredConstituentFactory</code> acts as a factory for
 * creating objects of class <code>LabeledScoredConstituent</code>.
 *
 * @author Christopher Manning
 */
public class LabeledScoredConstituentFactory implements ConstituentFactory {

  public Constituent newConstituent(int start, int end) {
    return new LabeledScoredConstituent(start, end);
  }


  public Constituent newConstituent(int start, int end, Label label, double score) {
    return new LabeledScoredConstituent(start, end, label, score);
  }

}
