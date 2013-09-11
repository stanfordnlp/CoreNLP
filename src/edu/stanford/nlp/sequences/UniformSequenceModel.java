package edu.stanford.nlp.sequences;

import java.util.Arrays;

/**
 * A prior which gives all sequences the same likelihood.
 * @author Teg Grenager (grenager@cs.stanford.edu)
 */

public class UniformSequenceModel implements SequenceModel {

  int numClasses;
  int[] possibleValues;
  int sequenceLength;
  double[] dist;

  public void updateSequenceElement(int[] sequence, int pos, int oldVal) {
    // do nothing
  }

  /**
   * Informs this sequence model that the value of the whole sequence is initialized to sequence
   *
   */
  public void setInitialSequence(int[] sequence) {
    // do nothing
  }

  /**
   * @return the length of the sequence
   */
  public int length() {
    return sequenceLength;
  }

  public int leftWindow() {
    return 0;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public int rightWindow() {
    return 0;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public int[] getPossibleValues(int position)   {
    return possibleValues;
  }

 /**
   * Computes the unnormalized log conditional distribution over values of the element 
   * at position pos in the sequence,
   * conditioned on the values of the elements in all other positions of the provided sequence.
   *
   * @param sequence the sequence containing the rest of the values fo condition on
   * @param pos      the position of the element to give a distribution for
   * @return an array of type double, representing a probability distribution; must sum to 1.0
   */
  public double[] scoresOf(int[] sequence, int pos) {
    return dist;
  }

  public double scoreOf(int[] sequence, int pos) {
    return 0.0;  // same for all
  }

  public double scoreOf(int[] sequence) {
    return 0.0; // same for all
  }

  public UniformSequenceModel(int numClasses, int sequenceLength) {
    this.numClasses = numClasses;
    this.possibleValues = new int[numClasses];
    for (int i=0; i<numClasses; i++) {
      possibleValues[i] = i;
    }
    this.sequenceLength = sequenceLength;
    dist = new double[numClasses];
    Arrays.fill(dist, 1.0/numClasses);
  }

}
