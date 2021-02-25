package edu.stanford.nlp.parser.shiftreduce;

import java.util.EmptyStackException;
import java.util.List;

import edu.stanford.nlp.parser.common.ParserConstraint;
import edu.stanford.nlp.trees.Tree;

/**
 * This transition type is intended to fix a situation where the
 * parser has shifted instead of made a Binary transition.  In that
 * situation, the oracle has a hard time coming up with a useful fix,
 * and perhaps after building the next constituent, the model will
 * know enough to fix the error
 */
public class LookbehindBinaryTransition implements Transition {
  final BinaryTransition binary;

  public LookbehindBinaryTransition(BinaryTransition binary) {
    this.binary = binary;
  }

  /**
   * Legal as long as there are at least two items on the state's stack.
   */
  @Override
  public boolean isLegal(State state, List<ParserConstraint> constraints) {
    if (state.stack.size() <= 2) {
      return false;
    }

    return binary.isLegal(state.popStack(), constraints);
  }


  /**
   * Add a binary node to the existing node on top of the stack
   */
  public State apply(State state) {
    return apply(state, 0.0);
  }

  /**
   * Add a binary node to the existing node on top of the stack
   */
  public State apply(State state, double scoreDelta) {
    Tree top = state.stack.peek();
    State popped = state.popStack();
    State reduced;
    try {
      reduced = binary.apply(popped, scoreDelta);
    } catch (EmptyStackException e) {
      System.out.println("FAILED " + this + " in state\n   " + state);
      throw e;
    }
    State result = new State(reduced.stack.push(top), reduced.transitions.pop().push(this), reduced.separators, reduced.sentence, reduced.tokenPosition, reduced.score, false);
    return result;
  }

  /**
   * Replaces two items on the stack with one item total
   */
  @Override
  public int stackSizeChange() {
    return -1;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof LookbehindBinaryTransition)) {
      return false;
    }
    return this.binary.equals(((LookbehindBinaryTransition) o).binary);
  }

  @Override
  public int hashCode() {
    return 452435 ^ binary.hashCode();
  }

  @Override
  public String toString() {
    return "Lookbehind" + binary.toString();
  }

  private static final long serialVersionUID = 1;
}
