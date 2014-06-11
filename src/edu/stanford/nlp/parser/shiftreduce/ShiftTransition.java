package edu.stanford.nlp.parser.shiftreduce;

/**
 * Transition that moves a single item from the front of the queue to
 * the top of the stack without making any other changes.
 */
public class ShiftTransition implements Transition {
  /**
   * Shifting is legal as long as the state is not finished and there
   * are more items on the queue to be shifted.
   * TODO: go through the papers and make sure they don't mention any
   * other conditions where one shouldn't shift
   */
  public boolean isLegal(State state) {
    if (state.finished) {
      return false;
    }
    if (state.tokenPosition >= state.sentence.size()) {
      return false;
    }
    return true;
  }

  /**
   * Add the new preterminal to the stack, increment the queue position.
   */
  public State apply(State state) {
    return new State(state.stack.push(state.sentence.get(state.tokenPosition)), state.sentence, state.tokenPosition + 1, state.score, false);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof ShiftTransition) {
      return true;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return 900967388; // a random int
  }

  @Override
  public String toString() {
    return "Shift";
  }
}
