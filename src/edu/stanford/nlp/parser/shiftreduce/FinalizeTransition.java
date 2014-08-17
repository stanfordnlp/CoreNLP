package edu.stanford.nlp.parser.shiftreduce;

import java.util.List;
import java.util.Set;
import edu.stanford.nlp.parser.common.ParserConstraint;

/**
 * Transition that finishes the processing of a state
 */
public class FinalizeTransition implements Transition {
  private final Set<String> rootStates;

  public FinalizeTransition(Set<String> rootStates) {
    this.rootStates = rootStates;
  }

  @Override
  public boolean isLegal(State state, List<ParserConstraint> constraints) {
    boolean legal = !state.finished && state.tokenPosition >= state.sentence.size() && state.stack.size() == 1 && rootStates.contains(state.stack.peek().value());
    if (!legal || constraints == null) {
      return legal;
    }

    for (ParserConstraint constraint : constraints) {
      if (constraint.start != 0 || constraint.end != state.sentence.size()) {
        continue;
      }
      if (!ShiftReduceUtils.constraintMatchesTreeTop(state.stack.peek(), constraint)) {
        return false;
      }
    }

    return true;
  }

  @Override
  public State apply(State state) {
    return apply(state, 0.0);
  }

  @Override
  public State apply(State state, double scoreDelta) {
    return new State(state.stack, state.transitions.push(this), state.separators, state.sentence, state.tokenPosition, state.score + scoreDelta, true);
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
