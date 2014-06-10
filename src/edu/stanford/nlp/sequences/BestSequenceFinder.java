package edu.stanford.nlp.sequences;


/**
 * An interface for classes capable of computing the best sequence given a SequenceModel.
 * @author Teg Grenager (grenager@stanford.edu)
 */
public interface BestSequenceFinder {

  /**
   * @return the sequence which is scored highest by the SequenceModel
   */
  public int[] bestSequence(SequenceModel sequenceModel);

}
