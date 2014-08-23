package edu.stanford.nlp.sequences;

/**
 * A class capable of listening to changes about a sequence,
 * represented as an array of type int.
 *
 * @author grenager
 */
public interface SequenceListener {

  /**
   * Informs this sequence listener that the value of the element at position pos has changed.
   * This allows this sequence model to update its internal model if desired.
   */
  public abstract void updateSequenceElement(int[] sequence, int pos, int oldVal);

  /**
   * Informs this sequence listener that the value of the whole sequence is initialized to sequence.
   */
  public abstract void setInitialSequence(int[] sequence);


}
