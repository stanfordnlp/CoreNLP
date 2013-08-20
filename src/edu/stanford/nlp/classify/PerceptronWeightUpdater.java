package edu.stanford.nlp.classify;

import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counters;

/**
 * @author Teg Grenager, Bill MacCartney &lt;wcmac@cs.stanford.edu&gt;
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 */
public class PerceptronWeightUpdater<T, ID> implements CounterWeightUpdater<T, ID> {

  // default values for no-arg constructor
  public final static double DEFAULT_LEARNING_RATE_MULTIPLIER = 1.0;
  
  private double learningRate = 1.0;
  private double learningRateMultiplier = DEFAULT_LEARNING_RATE_MULTIPLIER;
  
  public PerceptronWeightUpdater(double learningRateMultiplier) {
    this.learningRateMultiplier = learningRateMultiplier;
  }
  
  public PerceptronWeightUpdater() {
    this(DEFAULT_LEARNING_RATE_MULTIPLIER);
  }
  
  /**
   * Does the perceptron update for each data item. Ignores the losses.
   */
  public Counter<T> getUpdate(Counter<T> weights,
                                  Counter<T>[] goldVectors,
                                  Counter<T>[] guessedVectors,
                                  double[] losses,
                                  ID[] datumIDs,
                                  int iterSinceLastUpdate) {
    ClassicCounter<T> result = new ClassicCounter<T>();
    for (int i = 0; i < datumIDs.length; i++) {
      Counters.addInPlace(result, guessedVectors[i], -learningRate);
      Counters.addInPlace(result, goldVectors[i], learningRate);
    }
    return result;
  }

  public double getLearningRate() {
    return learningRate;
  }
  
  public double getLearningRateMultiplier() {
    return learningRateMultiplier;
  }
  
  /**
   * Causes the learning rate to be multiplied by the learning rate multiplier.
   * Call this at the end of each epoch.
   */
  public void endEpoch() {
    learningRate *= learningRateMultiplier;
  }
  
}
