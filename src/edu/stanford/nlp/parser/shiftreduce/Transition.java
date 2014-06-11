package edu.stanford.nlp.parser.shiftreduce;

import java.io.Serializable;

/**
 * An interface which defines a transition type in the shift-reduce
 * parser.  Expected transition types are shift, unary, binary,
 * finalize, and idle.
 */
public interface Transition extends Serializable {
  /**
   * Whether or not it is legal to apply this transition to this state.
   */
  public boolean isLegal(State state);

  /**
   * Applies this transition to get a new state.
   */
  public State apply(State state);

  /**
   * Applies this transition to get a new state.
   */
  public State apply(State state, double scoreDelta);
}
