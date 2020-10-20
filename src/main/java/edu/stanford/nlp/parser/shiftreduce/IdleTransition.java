package edu.stanford.nlp.parser.shiftreduce;

import java.util.List;
import edu.stanford.nlp.parser.common.ParserConstraint;

/**
 * Transition that literally does nothing
 */
public class IdleTransition implements Transition {
  /**
   * Legal only if the state is already finished
   */
  public boolean isLegal(State state, List<ParserConstraint> constraints) {
    return state.finished;
  }

  /**
   * Do nothing
   */
  public State apply(State state) {
    return apply(state, 0.0);
  }

  /**
   * Do nothing
   */
  public State apply(State state, double scoreDelta) {
    return new State(state.stack, state.transitions.push(this), state.separators, state.sentence, state.tokenPosition, state.score + scoreDelta, state.finished);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof IdleTransition) {
      return true;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return 586866881; // a random int
  }

  @Override
  public String toString() {
    return "Idle";
  }

  private static final long serialVersionUID = 1;  
}
