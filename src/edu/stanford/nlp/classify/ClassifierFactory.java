package edu.stanford.nlp.classify;

import java.io.Serializable;
import java.util.List;

import edu.stanford.nlp.ling.RVFDatum;

/**
 * A simple interface for training a Classifier from a Dataset of training
 * examples.
 *
 * @author Dan Klein
 *
 * Templatized by Sarah Spikes (sdspikes@cs.stanford.edu)
 */

public interface ClassifierFactory<L, F, C extends Classifier<L, F>> extends Serializable {

  @Deprecated //ClassifierFactory should implement trainClassifier(GeneralDataset) instead.
  public C trainClassifier(List<RVFDatum<L, F>> examples);

  public C trainClassifier(GeneralDataset<L,F> dataset);

}
