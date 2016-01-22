package edu.stanford.nlp.sequences;

/**
 * Class capable of scoring sequences of a fixed length, typically with a probability distribution over
 * such sequences.
 * @author Teg Grenager (grenager@stanford.edu)
 */
public interface SequenceModel {

  /**
   * @return the length of the sequences modeled by this SequenceModel
   */
  int length();

  /**
   * How many positions to the left a position is dependent on.
   * @return the size of the left window used by this sequence model
   */
  int leftWindow();

  /**
   * How many positions to the right a position is dependent on.
   * @return the size of the right window used by this sequence model
   */
  int rightWindow();

  /**
   * 0...leftWindow-1 etc are null, leftWindow...length+leftWindow-1 are words, length+leftWindow...length+leftWindow+rightWindow-1 are null;
   * @param position the position
   * @return the set of possible int values at this position, as an int array
   */
  int[] getPossibleValues(int position);

  /**
   * Computes the unnormalized log conditional distribution over values of the element
   * at position pos in the sequence,
   * conditioned on the values of the elements in all other positions of the provided sequence.
   * @param sequence the sequence containing the rest of the values to condition on
   * @param position the position of the element to give a distribution for
   * @return the scores of the possible tokens at the specified position in the sequence
   */
  double[] scoresOf(int[] sequence, int position);

  /**
   * Computes the unnormalized log conditional distribution over values of the element
   * at position pos in the sequence,
   * conditioned on the values of the elements in all other positions of the provided sequence.
   * @param sequence the sequence containing the rest of the values to condition on
   * @param position the position of the element to give a distribution for
   * @return the log score of the token at the specified position in the sequence
   */
  double scoreOf(int[] sequence, int position);

  /**
   * Computes the score assigned by this model to the whole sequence. Typically this will be an unnormalized
   * probability in log space (since the probabilities are small).
   * @param sequence the sequence to compute a score for
   * @return the score for the sequence
   */
  double scoreOf(int[] sequence);

}
