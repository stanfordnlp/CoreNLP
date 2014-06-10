package edu.stanford.nlp.sequences;


/**
 * An interface for classes capable of computing the best sequence given
 * a SequenceModel.
 *
 * Or it turns out that some implementations don't actually find the best
 * sequence but just sample a sequence.  (SequenceSampler, I'm looking at
 * you.)  I guess this makes sense if all sequences are scored equally.
 *
 * @author Teg Grenager (grenager@stanford.edu)
 */
public interface BestSequenceFinder {

  /**
   * Finds the best sequence for the sequence model based on its scoring.
   *
   * @return The sequence which is scored highest by the SequenceModel
   */
  public int[] bestSequence(SequenceModel sequenceModel);

}
