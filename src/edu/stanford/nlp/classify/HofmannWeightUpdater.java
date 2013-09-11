package edu.stanford.nlp.classify;

import java.util.List;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
/**
 * @author grenager
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 */

public class HofmannWeightUpdater<T> implements CounterWeightUpdater<T, T> {
  
  // the first set of weights we were passed, possibly 0.0
  Counter<T> originalWeights;
  
  // these represent the constraints we've remembered so far
  // as well as the Lagrange multipliers that we have found for them
  List<Double> lossMinusOrigScoreDiffs;
  List<Double> alphas;
  List<Counter<T>> featureDiffs;

  /**
   * Ignores weights, unless this is the first time called
   */
  public Counter<T> getUpdate(Counter<T> weights, Counter<T>[] goldVectors, Counter<T>[] guessedVectors, 
                               double[] losses, T[] datumIDs, int iterSinceLastUpdate) {
    if (originalWeights==null) originalWeights = weights;
    // now add the new constraints
    for (int i=0; i<goldVectors.length; i++) {
      Counter<T> featureDiff = goldVectors[i];
      Counters.subtractInPlace(featureDiff, guessedVectors[i]);
      if(true)
      Counters.retainNonZeros(featureDiff);
      featureDiff.setCount(datumIDs[i], 1.0); // creates a slack variable
      double origScoreDiff = Counters.dotProduct(featureDiff, originalWeights);
      // now add this new constraint to our constraints
      lossMinusOrigScoreDiffs.add(new Double(losses[i]-origScoreDiff));
      alphas.add(new Double(0.0));
      featureDiffs.add(featureDiff);
    }
    // now do the optimization with this set of constraints to get new alphas
    
    
    // now construct the new weight vector to return
    ClassicCounter<T> update = new ClassicCounter<T>();
    for (int i=0; i<alphas.size(); i++) {
      Counters.addInPlace(update, Counters.scale(featureDiffs.get(i), alphas.get(i)));
    }
    return update;
  }
  
  public void endEpoch() {
    
  }

}
