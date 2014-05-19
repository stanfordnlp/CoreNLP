package edu.stanford.nlp.parser.shiftreduce;

/**
 * Transition that literally does nothing
 */
public class IdleTransition implements Transition {
  /**
   * Legal only if the state is already finished
   */
  public boolean isLegal(State state) {
    return state.finished;
  }

  /**
   * Do nothing
   */
  public State apply(State state) {
    return state;
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
}
