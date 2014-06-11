package edu.stanford.nlp.parser.shiftreduce;

/**
 * Transition that finishes the processing of a state
 */
public class FinalizeTransition implements Transition {
  public boolean isLegal(State state) {
    return !state.finished && state.tokenPosition >= state.sentence.size() && state.stack.size() == 1;
  }

  public State apply(State state) {
    return new State(state.stack, state.transitions.push(this), state.sentence, state.tokenPosition, state.score, true);    
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof FinalizeTransition) {
      return true;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return 593744340; // a random int
  }


  @Override
  public String toString() {
    return "Finalize";
  }

  private static final long serialVersionUID = 1;  
}
