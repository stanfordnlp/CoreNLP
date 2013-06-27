package edu.stanford.nlp.classify;

import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.stats.Counter;

import java.io.Serializable;
import java.util.Collection;

/**
 * A simple interface for classifying and scoring data points, implemented
 * by most of the classifiers in this package.  A basic Classifier
 * works over a List of categorical features.  For classifiers over
 * real-valued features, see {@link RVFClassifier}.
 *
 * @author Dan Klein
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 *
 * @param <L> The type of the label(s) in each Datum
 * @param <F> The type of the features in each Datum
 */

public interface Classifier<L, F> extends Serializable {
  public L classOf(Datum<L, F> example);

  public Counter<L> scoresOf(Datum<L, F> example);

  public Collection<L> labels();
}
