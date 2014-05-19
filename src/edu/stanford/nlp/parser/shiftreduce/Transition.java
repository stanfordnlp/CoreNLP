package edu.stanford.nlp.parser.shiftreduce;

/**
 * An interface which defines a transition type in the shift-reduce
 * parser.  Expected transition types are shift, unary, binary,
 * finalize, and idle.
 */
public interface Transition {
  /**
   * Whether or not it is legal to apply this transition to this state.
   */
  public boolean isLegal(State state);

  /**
   * Applies this transition to get a new state.
   * TODO: we need a way to include the score change, if any
   */
  public State apply(State state);
}
