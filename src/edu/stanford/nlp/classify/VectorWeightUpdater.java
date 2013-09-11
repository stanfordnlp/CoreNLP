package edu.stanford.nlp.classify;

import org.apache.commons.math.linear.RealVector;

/**
 * An interface for classes that can take an Apache commons RealVector based weight vector and a prediction error, and update
 * the weight vector.
 * 
 * @author grenager
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 * @author Daniel Cer (reworked from CounterWeightUpdater)
 */
public interface VectorWeightUpdater<F, ID> {

  public RealVector getUpdate(RealVector weights, RealVector[] goldVectors, RealVector[] guessedVectors, double[] losses,
                               ID[] datumIDs, int iterSinceLastUpdate);
  
  public void endEpoch();
}