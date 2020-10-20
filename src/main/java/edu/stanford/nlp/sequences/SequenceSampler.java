package edu.stanford.nlp.sequences;

import edu.stanford.nlp.math.ArrayMath;

/**
 * This class will sample an output from a sequence model.  It assumes that
 * the scores are (unnormalized) log-probabilities.  It works by sampling
 * each variable in order, conditioned on the previous variables.
 *
 * @author Jenny Finkel
 */
public class SequenceSampler implements BestSequenceFinder {

  /**
   * Samples each label in turn from left to right.
   *
   * @return an array containing the int tags of the best sequence
   */
  @Override
  public int[] bestSequence(SequenceModel ts) {
    // Also allocate space for rightWindow, just in case sequence model uses
    // it, even though this implementation doesn't. Probably it shouldn't,
    // or the left-to-right sampling is invalid, but our test classes do.
    int[] sample = new int[ts.length()+ts.leftWindow()+ts.rightWindow()];

    for (int pos = ts.leftWindow(); pos < sample.length - ts.rightWindow(); pos++) {
      double[] scores = ts.scoresOf(sample, pos);
      for (int i = 0; i < scores.length; i++) {
        scores[i] = Math.exp(scores[i]);
      }
      ArrayMath.normalize(scores);
      int l = ArrayMath.sampleFromDistribution(scores);
      sample[pos] = ts.getPossibleValues(pos)[l];
    }

    return sample;
  }

}
