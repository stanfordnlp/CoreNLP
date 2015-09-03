package edu.stanford.nlp.classify;

import java.io.Serializable;

/**
 * A simple interface for training a Classifier from a Dataset of training
 * examples.
 *
 * @author Dan Klein
 *
 * Templatized by Sarah Spikes (sdspikes@cs.stanford.edu)
 */

public interface ClassifierFactory<L, F, C extends Classifier<L, F>> extends Serializable {

  public C trainClassifier(GeneralDataset<L,F> dataset);

}
