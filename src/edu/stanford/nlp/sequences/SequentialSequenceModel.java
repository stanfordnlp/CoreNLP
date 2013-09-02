package edu.stanford.nlp.sequences;

import edu.stanford.nlp.math.ArrayMath;
import java.util.Arrays;

/**
 * @author Mengqiu Wang
 */
public class SequentialSequenceModel implements SequenceModel {

  SequenceModel[] models = null;
  int[] modelLenBound = null;
  int combinedModelLength = -1;
  /**
   * Computes the distribution over values of the element at position pos in the sequence,
   * conditioned on the values of the elements in all other positions of the provided sequence.
   *
   * @param sequence the sequence containing the rest of the values to condition on
   * @param pos      the position of the element to give a distribution for
   * @return an array of type double, representing a probability distribution; must sum to 1.0
   */
  public double[] scoresOf(int[] sequence, int pos) {
    int modelIndex = 0;
    for (; modelIndex < modelLenBound.length; modelIndex++) {
      if (pos < modelLenBound[modelIndex])
        break;
    }
    double score = 0;
    /* Temporary disabled, since we only care about conditional prob
    int oModelIndex = 0;
    for (; oModelIndex < modelLenBound.length; oModelIndex++) {
      if (oModelIndex != modelIndex) {
        int oBegin = 0;
        if (oModelIndex > 0)
          oBegin = modelLenBound[oModelIndex-1];
        int oEnd = modelLenBound[oModelIndex];
        int[] oSubseq = Arrays.copyOfRange(sequence, oBegin, oEnd);
        score += models[oModelIndex].scoreOf(oSubseq); 
      }
    }
    */

    int begin = 0;
    if (modelIndex > 0)
      begin = modelLenBound[modelIndex-1];
    int end = modelLenBound[modelIndex];
    int[] subseq = Arrays.copyOfRange(sequence, begin, end);
    int newPos = pos - begin;
    double[] cond = models[modelIndex].scoresOf(subseq, newPos);
    /* Temporary disabled, since we only care about conditional prob
    for (int i = 0; i < cond.length; i++) {
      cond[i] += score;
    }
    */
    return cond;
  }

  public double scoreOf(int[] sequence, int pos) {
    return scoresOf(sequence, pos)[sequence[pos]];
  }

  /**
   * Computes the score assigned by this model to the provided sequence. Typically this will be a
   * probability in log space (since the probabilities are small).
   *
   * @param sequence the sequence to compute a score for
   * @return the score for the sequence
   */
  public double scoreOf(int[] sequence) {
    double score = 0;
    int modelIndex = 0;
    for (; modelIndex < modelLenBound.length; modelIndex++) {
      int begin = 0;
      if (modelIndex > 0)
        begin = modelLenBound[modelIndex-1];
      int end = modelLenBound[modelIndex];
      int[] subseq = Arrays.copyOfRange(sequence, begin, end);
      score += models[modelIndex].scoreOf(subseq); 
    }
    return score;
  }

  /**
   * @return the length of the sequence
   */
  public int length() {
    return combinedModelLength;
  }

  public int leftWindow() {
    return models[0].leftWindow();
  }

  public int rightWindow() {
    return 0;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public int[] getPossibleValues(int position) {
    return models[0].getPossibleValues(position);
  }

  public SequentialSequenceModel(SequenceModel[] models){
    this.models = models;
    this.modelLenBound = new int[models.length];
    int currLenBound = 0;
    for (int i = 0; i < models.length; i++) {
      currLenBound += models[i].length();
      this.modelLenBound[i] = currLenBound;
    }
    this.combinedModelLength = currLenBound;
  }
}
