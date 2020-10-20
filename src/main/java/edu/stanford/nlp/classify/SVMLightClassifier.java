package edu.stanford.nlp.classify;

import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.RVFDatum;

/**
 * This class represents a trained SVM Classifier.  It is actually just a
 * LinearClassifier, but it can have a Platt (sigmoid) model overlaying
 * it for the purpose of producing meaningful probabilities.
 *
 * @author Jenny Finkel
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (templatization)
 */

public class SVMLightClassifier<L, F> extends LinearClassifier<L, F> {

  /**
   * 
   */
  private static final long serialVersionUID = 1L;
  public LinearClassifier<L, L> platt = null;

  public SVMLightClassifier(ClassicCounter<Pair<F, L>> weightCounter, ClassicCounter<L> thresholds) {
    super(weightCounter, thresholds);
  }

  public SVMLightClassifier(ClassicCounter<Pair<F, L>> weightCounter, ClassicCounter<L> thresholds, LinearClassifier<L, L> platt) {
    super(weightCounter, thresholds);
    this.platt = platt;
  }

  public void setPlatt(LinearClassifier<L, L> platt) {
    this.platt = platt;
  }

  /**
   * Returns a counter for the log probability of each of the classes
   * looking at the the sum of e^v for each count v, should be 1
   * Note: Uses SloppyMath.logSum which isn't exact but isn't as
   * offensively slow as doing a series of exponentials
   */
  @Override
  public Counter<L> logProbabilityOf(Datum<L, F> example) {
    if (platt == null) {
      throw new UnsupportedOperationException("If you want to ask for the probability, you must train a Platt model!");
    }
    Counter<L> scores = scoresOf(example);
    scores.incrementCount(null);
    Counter<L> probs = platt.logProbabilityOf(new RVFDatum<>(scores));
    //System.out.println(scores+" "+probs);
    return probs;
  }

  /**
   * Returns a counter for the log probability of each of the classes
   * looking at the the sum of e^v for each count v, should be 1
   * Note: Uses SloppyMath.logSum which isn't exact but isn't as
   * offensively slow as doing a series of exponentials
   */
  @Override
  public Counter<L> logProbabilityOf(RVFDatum<L, F> example) {
    if (platt == null) {
      throw new UnsupportedOperationException("If you want to ask for the probability, you must train a Platt model!");
    }
    Counter<L> scores = scoresOf(example);
    scores.incrementCount(null);
    Counter<L> probs = platt.logProbabilityOf(new RVFDatum<>(scores));
    //System.out.println(scores+" "+probs);
    return probs;
  }
}
