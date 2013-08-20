package edu.stanford.nlp.classify;

import edu.stanford.nlp.stats.Counter;

/**
 * An interface for classes that can take a Counter based weight vector and a prediction error, and update
 * the weight vector.
 * 
 * @author grenager
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 * @author Daniel Cer (use of Counter instead of ClassicCounter)
 */
public interface CounterWeightUpdater<F, ID> {

  public Counter<F> getUpdate(Counter<F> weights, Counter<F>[] goldVectors, Counter<F>[] guessedVectors, double[] losses,
                               ID[] datumIDs, int iterSinceLastUpdate);
  
  public void endEpoch();
}
