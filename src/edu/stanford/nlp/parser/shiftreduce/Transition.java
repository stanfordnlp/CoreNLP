package edu.stanford.nlp.parser.shiftreduce;

import java.io.Serializable;
import java.util.List;

import edu.stanford.nlp.parser.common.ParserConstraint;

/**
 * An interface which defines a transition type in the shift-reduce
 * parser.  Expected transition types are shift, unary, binary,
 * finalize, and idle.  
 * <br>
 * There is also a compound unary transition for combining multiple
 * unary transitions into one, which lets us prevent the parser from
 * creating arbitrary unary transition sequences.
 */
public interface Transition extends Serializable {
  /**
   * Whether or not it is legal to apply this transition to this state.
   */
  public boolean isLegal(State state, List<ParserConstraint> constraints);

  /**
   * Applies this transition to get a new state.
   */
  public State apply(State state);

  /**
   * Applies this transition to get a new state.
   */
  public State apply(State state, double scoreDelta);
}
