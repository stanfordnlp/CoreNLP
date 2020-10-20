package edu.stanford.nlp.fsm;


/**
 * @author Dan Klein (klein@cs.stanford.edu)
 */
public interface AutomatonMinimizer {
  public TransducerGraph minimizeFA(TransducerGraph unminimizedFA);
}
