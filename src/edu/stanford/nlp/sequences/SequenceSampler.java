package edu.stanford.nlp.sequences;

import edu.stanford.nlp.math.ArrayMath;
import java.util.Arrays;

/**
 * This class will sample an output from a sequence model.  It
 * assumes that the scores are (unnormalized) log-probabilities.  It works by sampling
 * each variable in order, conditioned on the previous variables.
 *
 * @author Jenny Finkel
 */
public class SequenceSampler implements BestSequenceFinder {

  /**
   * A class for testing.
   */
  private static class TestSequenceModel implements SequenceModel {

    private int[] correctTags = {0, 0, 1, 2, 3, 4, 5, 6, 7, 6, 5, 4, 3, 2, 1, 0, 0};
    private int[] allTags = {1, 2, 3, 4, 5, 6, 7, 8, 9};
    private int[] midTags = {0, 1, 2, 3};
    private int[] nullTags = {0};

    public int length() {
      return correctTags.length - leftWindow() - rightWindow();
    }

    public int leftWindow() {
      return 2;
    }

    public int rightWindow() {
      return 0;
    }

    public int[] getPossibleValues(int pos) {
      if (pos < leftWindow() || pos >= leftWindow() + length()) {
        return nullTags;
      }
      if (correctTags[pos] < 4) {
        return midTags;
      }
      return allTags;
    }

    public double scoreOf(int[] tags, int pos) {
      return 1.0;
    }

    public double scoreOf(int[] sequence) {
      throw new UnsupportedOperationException();
    }

    public double[] scoresOf(int[] tags, int pos) {
      int[] tagsAtPos = getPossibleValues(pos);
      double[] scores = new double[tagsAtPos.length];
      Arrays.fill(scores, 1.0);
      return scores;
    }

  } // end class TestSequenceModel


  private static String arrayToString(int[] x) {
    StringBuilder sb = new StringBuilder("(");
    for (int j = 0; j < x.length; j++) {
      sb.append(x[j]);
      if (j != x.length - 1) {
        sb.append(", ");
      }
    }
    sb.append(")");
    return sb.toString();
  }

  public static void main(String[] args) {
    SequenceSampler ti = new SequenceSampler();
    SequenceModel ts = new TestSequenceModel();
    int[] bestTags = ti.bestSequence(ts);
    System.out.println("The best sequence is ... " + arrayToString(bestTags));
  }

  /**
   * Runs the Viterbi algorithm on the sequence model given by the TagScorer
   * in order to find the best sequence.
   * @return an array containing the int tags of the best sequence
   */
  public int[] bestSequence(SequenceModel ts) {
    
    int[] sample = new int[ts.length()+ts.leftWindow()];

    for (int pos = ts.leftWindow(); pos < sample.length; pos++) {
      double[] scores = ts.scoresOf(sample, pos);
      double total = 0.0;
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
