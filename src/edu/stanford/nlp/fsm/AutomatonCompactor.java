package edu.stanford.nlp.fsm;


/**
 * Compacts a weighted finite automaton. The returned automaton accepts at least the language
 * accepted by the uncompactedFA (and perhaps more). The returned automaton is also weighted.
 *
 * @author Dan Klein (klein@cs.stanford.edu)
 */
public interface AutomatonCompactor {
  public TransducerGraph compactFA(TransducerGraph ucompactedFA);
}
