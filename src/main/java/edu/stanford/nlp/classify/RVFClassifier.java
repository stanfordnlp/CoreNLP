package edu.stanford.nlp.classify;

import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.Counter;

import java.io.Serializable;

/**
 * A simple interface for classifying and scoring data points with
 * real-valued features.  Implemented by the linear classifier.
 *
 * @author Jenny Finkel
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 */

public interface RVFClassifier<L, F> extends Serializable {
  public L classOf(RVFDatum<L, F> example);

  public Counter<L> scoresOf(RVFDatum<L, F> example);
}
